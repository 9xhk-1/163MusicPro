package com.qinghe.music163pro.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用内精细音量（40 级，每级 1.2 dB），全设备共用同一套刻度。
 *
 * 背景：部分手表系统的媒体音量只有 6 级，按一次音量键跳变过大，无法细调。
 *
 * 做法：
 *   1. 本应用播放时把系统媒体音量固定顶到最大（"锚点"），由系统提供最大增益；
 *   2. 真正的响度由本类算出的数字增益控制，作用于 MediaPlayer / ExoPlayer：
 *      gain = 10 ^ (dB / 20)，dB 从 0（最大）线性降到 -48（最低一级），0 级为静音。
 *   3. 等级持久化，退出/重装前一直生效。
 *
 * 输出设备统一（手表扬声器 / TWS / AirPods Pro）：
 *   - 只有这一个 level，不区分输出设备，耳机和手表共用 0-40 的同一范围。
 *   - 手表扬声器：系统媒体音量被"锚点"顶到最大作为满刻度，响度完全由本类的数字
 *     增益（40 级曲线）控制，左右耳同步、无 AVRCP 往返延迟、不触发耳机提示音。
 *   - 蓝牙耳机（AirPods / TWS）：开启绝对音量后，耳机耳柄/按键直接改系统
 *     STREAM_MUSIC（而不是走 MediaSession 的 VolumeProvider），物理音量由耳机
 *     自己决定。此时应用不再叠加数字增益（增益恒为 1.0，避免"物理×数字"双重衰减
 *     让中低档几乎听不到），应用内 level 仅用于在手表上显示/调节耳机物理音量；
 *     收到系统音量变化广播时把物理音量折算回 level，让手表显示与耳机保持一致。
 *   - 任何一次等级变化都会立刻把增益刷到当前播放器（GainApplier），
 *     避免"调完没生效、切下一首才变"这类跳变；监听设备接入/断开，耳机断开时
 *     通知播放器暂停，避免声音突然从手表扬声器以顶满的系统音量外放。
 */
public final class AppVolumeManager {

    private static final String TAG = "AppVolume";

    private static final String PREFS_NAME = "music163_app_volume";
    private static final String KEY_LEVEL = "app_volume_level";

    /** 精细音量总级数：40 级（40 级 = 系统最大音量）。 */
    public static final int MAX_LEVEL = 40;

    /**
     * 最低非零一级的衰减量（dB）。0 级是完全静音。
     * 底部拉到 -54 dB 并配合 GAIN_GAMMA 凸曲线，把"很小的声音"这一段的行程大幅扩展：
     * 低档位衰减得更快，所以从满音量往下每一级都明显更轻，不会再出现
     * "2 还很响、0 突然没"这种低音量断层。
     */
    private static final float FLOOR_DB = -54f;

    /**
     * 增益曲线凸度。t = level / MAX_LEVEL，db = FLOOR_DB * (1 - t^GAMMA)。
     * GAMMA > 1 让低档位更靠下，把动态范围集中到底部，拉开低音量的跨度；
     * GAMMA = 1 时退化为线性 dB（每级等 dB）。取 1.5 让底部更舒展又不至于顶部过暗。
     */
    private static final float GAIN_GAMMA = 1.5f;

    /** 等级量纲版本：2 = 40 级 / 底部 -48 dB。用于把旧的 30 级存档换算过来。 */
    private static final String KEY_LEVEL_SCALE = "app_volume_scale";
    private static final int LEVEL_SCALE = 2;
    private static final int LEGACY_MAX_LEVEL = 30;
    private static final float LEGACY_FLOOR_DB = -30f;

    private static AppVolumeManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int level = MAX_LEVEL;
    /** 上一次对齐后系统媒体音量的值，仅用于避免重复打日志。 */
    private int lastAnchoredSystemVolume = -1;

    /** 蓝牙耳柄相对步进：每个耳柄档 = 2 个应用内等级，避免把系统仅有的几级音量折算成几十级（一调跳十几格）。 */
    private static final int BT_STEP_LEVELS = 2;
    /** 最近一次从耳机读到的系统媒体音量，用于判断耳柄调大/调小方向。 */
    private int lastBtSystemVolume = -1;
    /** 应用内（手表音量键）把等级推到耳机物理音量时的回声抑制标记，避免其广播被当成耳柄事件再步进。 */
    private boolean btVolumeEchoGuard = false;
    private final Runnable btEchoGuardReset = new Runnable() {
        @Override
        public void run() {
            btVolumeEchoGuard = false;
        }
    };

    /** 增益套用器：由播放器注册。等级一变就立即生效，不需要等到下一首歌。 */
    public interface GainApplier {
        void applyGain();
    }

    /** 等级变化监听：UI 浮层、媒体会话 VolumeProvider 等用来同步显示。 */
    public interface LevelChangeListener {
        void onLevelChanged(int level);
    }

    private GainApplier gainApplier;
    private final List<LevelChangeListener> levelListeners = new ArrayList<>();
    /** 耳机物理音量推送监听：由服务把应用内等级经 VolumeProvider 推到耳机；与 UI 监听分离，
     *  耳柄事件不触发它，避免把耳柄刚调大的音量又压回去（蓝牙整数档位四舍五入）。 */
    private LevelChangeListener headphonePushListener;

    /** 输出设备变化监听（蓝牙耳机接入/断开）。 */
    public interface OutputChangeListener {
        void onOutputChanged(boolean bluetoothNow);
    }

    private final List<OutputChangeListener> outputListeners = new ArrayList<>();
    private boolean lastBluetoothOutput;
    private boolean deviceCallbackRegistered;

    private AppVolumeManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int saved = prefs.getInt(KEY_LEVEL, -1);
        int scale = prefs.getInt(KEY_LEVEL_SCALE, 1);
        if (scale != LEVEL_SCALE) {
            // 从旧的 30 级量纲换算：按"dB 等响"换算，而不是按比例，
            // 否则旧的 24/30（-6 dB）会变成 32/40（-9.6 dB），升级后白白掉 3.6 dB。
            if (saved > 0) {
                // 按"等响度（dB）"换算：先由旧量纲算出旧 dB，再反解新量纲下的 level。
                // 新曲线：db = FLOOR_DB * (1 - (level/MAX)^GAMMA)
                //   → level = MAX * (1 - db/FLOOR_DB)^(1/GAMMA)
                float legacyDb = LEGACY_FLOOR_DB * (1f - saved / (float) LEGACY_MAX_LEVEL);
                float ratio = 1f - legacyDb / FLOOR_DB;
                float t = (float) Math.pow(Math.max(0f, ratio), 1f / GAIN_GAMMA);
                saved = Math.max(1, Math.min(MAX_LEVEL, Math.round(t * MAX_LEVEL)));
            } else if (saved == 0) {
                saved = 0;
            } else {
                saved = levelFromSystemVolume();
            }
            prefs.edit()
                    .putInt(KEY_LEVEL, saved)
                    .putInt(KEY_LEVEL_SCALE, LEVEL_SCALE)
                    .apply();
            Log.i(TAG, "音量量纲升级到 " + MAX_LEVEL + " 级，换算后 level=" + saved);
        } else if (saved < 0 || saved > MAX_LEVEL) {
            // 首次运行：按当前系统音量百分比折算成应用内等级，
            // 避免升级后第一次播放突然变响（锚点会把系统音量顶满）。
            saved = levelFromSystemVolume();
            prefs.edit().putInt(KEY_LEVEL, saved).apply();
        }
        level = saved;
        registerAudioDeviceCallback();
        Log.i(TAG, "应用内音量初始化: level=" + level + "/" + MAX_LEVEL
                + "，当前输出=" + getOutputLabel());
    }

    public static synchronized AppVolumeManager init(Context context) {
        if (instance == null && context != null) {
            instance = new AppVolumeManager(context);
        }
        return instance;
    }

    /** 可能为 null（Application 尚未初始化时）。 */
    public static AppVolumeManager get() {
        return instance;
    }

    /** 安全取增益：未初始化时返回 1.0，保证播放器行为不受影响。 */
    public static float gainOf() {
        AppVolumeManager manager = instance;
        return manager == null ? 1f : manager.getGain();
    }

    public int getLevel() {
        return level;
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    /** 0-100 的百分比，仅用于显示。 */
    public int getPercent() {
        return Math.round(level * 100f / MAX_LEVEL);
    }

    /**
     * 当前等级对应的线性增益，范围 0.0 - 1.0。每级非线性衰减（见 GAIN_GAMMA）。
     *
     * 蓝牙耳机（AirPods / TWS）特例：开启绝对音量后，响度完全由耳机物理音量
     * （系统 STREAM_MUSIC / AVRCP）决定。若这里再叠加一层数字增益，会变成
     * "物理音量 × 数字增益" 双重衰减，中低档几乎听不到。所以蓝牙输出时直接返回
     * 1.0（不衰减），应用内 level 仅用于在手表上显示 / 调节耳机物理音量，
     * 真正的响度交给耳机自己的音量刻度。
     */
    public float getGain() {
        if (isBluetoothOutput()) {
            return 1.0f;
        }
        return gainForLevel(level);
    }

    /** 纯函数：等级 -> 线性增益（0.0-1.0）。便于单独校验。 */
    public static float gainForLevel(int level) {
        if (level <= 0) {
            return 0f;
        }
        if (level >= MAX_LEVEL) {
            return 1f;
        }
        float t = (float) level / MAX_LEVEL;
        float shaped = (float) Math.pow(t, GAIN_GAMMA);
        float db = FLOOR_DB * (1f - shaped);
        return (float) Math.pow(10d, db / 20d);
    }

    /**
     * 设置等级并持久化，返回是否发生了变化。
     * 变化后立即把增益刷到播放器并通知所有监听者（UI / 媒体会话）。
     */
    public boolean setLevel(int newLevel) {
        int clamped = Math.max(0, Math.min(MAX_LEVEL, newLevel));
        if (clamped == level) {
            anchorSystemVolume();
            return false;
        }
        level = clamped;
        prefs.edit()
                .putInt(KEY_LEVEL, clamped)
                .putInt(KEY_LEVEL_SCALE, LEVEL_SCALE)
                .apply();
        anchorSystemVolume();
        dispatchGainChange(true);
        if (headphonePushListener != null) {
            try {
                headphonePushListener.onLevelChanged(level);
            } catch (Exception e) {
                Log.w(TAG, "推送耳机物理音量失败", e);
            }
        }
        Log.i(TAG, "应用内音量: " + clamped + "/" + MAX_LEVEL
                + " (" + getPercent() + "%, gain=" + getGain() + ")");
        return true;
    }

    public boolean stepUp() {
        return setLevel(level + 1);
    }

    public boolean stepDown() {
        return setLevel(level - 1);
    }

    /** 播放器注册一个增益套用器，全局只保留一个。 */
    public void setGainApplier(GainApplier applier) {
        this.gainApplier = applier;
    }

    public void addLevelChangeListener(LevelChangeListener listener) {
        if (listener != null && !levelListeners.contains(listener)) {
            levelListeners.add(listener);
        }
    }

    public void removeLevelChangeListener(LevelChangeListener listener) {
        levelListeners.remove(listener);
    }

    /**
     * 音量变化 / 播放器重建 / 输出设备切换后调用：把当前等级重新压到播放器上，
     * 并再次把系统媒体音量顶到锚点。这样 ROM 在 prepare 或蓝牙绝对音量协商时
     * 把音量重置掉，也不会出现"切歌后声音回到满音量"。
     */
    public void refreshGain() {
        anchorSystemVolume();
        dispatchGainChange(false);
    }

    /**
     * 蓝牙耳柄 / 耳机按键引起的系统音量变化：按"事件方向"相对步进 BT_STEP_LEVELS 个
     * 应用内等级，让耳柄像手表音量键一样精准（每调一格 = 2 级），而不是把系统仅有的
     * 几级音量绝对折算成几十级（那样一调就跳十几格）。
     *
     * 这里只更新应用内等级与手表显示，并【不】把等级反推回耳机物理音量
     * （volumeProvider.setCurrentVolume）——否则会因蓝牙整数档位四舍五入，把耳柄刚
     * 调大的音量又压回去。耳机物理响度仍由耳柄本身决定。
     *
     * @return 等级是否发生了变化
     */
    public boolean stepLevelFromBtVolume() {
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return false;
            }
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) {
                return false;
            }
            if (lastBtSystemVolume < 0) {
                // 首次见到该系统音量，仅做基线，不步进
                lastBtSystemVolume = cur;
                return false;
            }
            int delta = cur - lastBtSystemVolume;
            lastBtSystemVolume = cur;
            if (delta == 0) {
                return false;
            }
            if (btVolumeEchoGuard) {
                // 这是手表音量键路径经 setCurrentVolume 推下去的回声，忽略
                btVolumeEchoGuard = false;
                mainHandler.removeCallbacks(btEchoGuardReset);
                return false;
            }
            int dir = delta > 0 ? 1 : -1;
            int target = level + dir * BT_STEP_LEVELS;
            int clamped = Math.max(0, Math.min(MAX_LEVEL, target));
            if (clamped == level) {
                return false;
            }
            level = clamped;
            prefs.edit()
                    .putInt(KEY_LEVEL, clamped)
                    .putInt(KEY_LEVEL_SCALE, LEVEL_SCALE)
                    .apply();
            anchorSystemVolume();
            dispatchGainChange(true);
            Log.i(TAG, "蓝牙耳柄音量 " + (dir > 0 ? "+" : "-") + BT_STEP_LEVELS
                    + " 级 -> " + level + "/" + MAX_LEVEL + " (" + getPercent() + "%)");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "处理蓝牙耳柄音量失败", e);
            return false;
        }
    }

    /**
     * 手表音量键等应用内改动，在把等级推到耳机物理音量前调用：打上回声抑制标记，
     * 避免其引起的 VOLUME_CHANGED_ACTION 被 stepLevelFromBtVolume 误当成耳柄事件再步进。
     */
    public void markBtVolumePush() {
        btVolumeEchoGuard = true;
        mainHandler.removeCallbacks(btEchoGuardReset);
        mainHandler.postDelayed(btEchoGuardReset, 400);
    }

    /** 注册耳机物理音量推送监听（由服务把应用内等级经 VolumeProvider 推到耳机）。与 UI 监听分离。 */
    public void setHeadphonePushListener(LevelChangeListener listener) {
        this.headphonePushListener = listener;
    }

    /** 主线程执行套用 + 通知，避免 ExoPlayer 在非应用线程被调用。 */
    private void dispatchGainChange(final boolean notifyLevel) {
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                GainApplier applier = gainApplier;
                if (applier != null) {
                    try {
                        applier.applyGain();
                    } catch (Exception e) {
                        Log.w(TAG, "套用应用内增益失败", e);
                    }
                }
                if (notifyLevel) {
                    for (LevelChangeListener listener : new ArrayList<>(levelListeners)) {
                        try {
                            listener.onLevelChanged(level);
                        } catch (Exception e) {
                            Log.w(TAG, "音量变化回调异常", e);
                        }
                    }
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    /**
     * 把系统媒体音量对齐到最大，作为应用内音量的"满刻度"基准。
     *
     * 每次都校验而不是只做一次：ROM 的省电策略、系统音量面板、蓝牙 AVRCP
     * 绝对音量协商都可能把系统音量改回去。一旦没对齐，应用内 100% 就不再是
     * 设备最大响度，表现为"耳机和手表的音量范围对不上"。
     *
     * 某些系统（开启免打扰、家长模式等）会拒绝修改，异常时静默跳过。
     */
    public void anchorSystemVolume() {
        try {
            // 蓝牙绝对音量模式：系统会按 VolumeProvider 上报的 currentVolume 直接驱动耳机物理音量。
            // 若这里再把 STREAM_MUSIC 顶到最大，等于命令耳机回到满音量，约 0.5s（蓝牙往返延迟）
            // 后耳机被顶回原音量，表现为"调完音量又弹回去"。故蓝牙输出时不做锚点，
            // 音量完全交给数字增益 + VolumeProvider 控制，回弹问题自然消失。
            if (isBluetoothOutput()) {
                return;
            }
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) {
                return;
            }
            int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (current < max) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
                if (lastAnchoredSystemVolume != max) {
                    Log.i(TAG, "系统媒体音量已对齐到最大值(" + max + ")，音量由应用内 "
                            + MAX_LEVEL + " 级控制");
                    lastAnchoredSystemVolume = max;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "对齐系统媒体音量失败，下次会重试", e);
        }
    }

    /** 把当前增益作用到指定的 MediaPlayer。 */
    public void applyTo(MediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            float gain = getGain();
            player.setVolume(gain, gain);
        } catch (Exception e) {
            Log.w(TAG, "MediaPlayer 设置音量失败", e);
        }
    }

    // ─────────────────────── 输出设备（蓝牙耳机适配） ───────────────────────

    /**
     * 当前是否走蓝牙耳机输出。
     * 优先用 A2DP 路由状态（TWS / AirPods 都走这条），再退回设备列表判断，
     * 以兼容 BLE Audio 耳机。
     */
    public boolean isBluetoothOutput() {
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return false;
            }
            try {
                if (am.isBluetoothA2dpOn()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            // BLE Audio 耳机（Android 12+）不看 A2DP 状态，只能查设备列表。
            if (Build.VERSION.SDK_INT >= 31) {
                for (AudioDeviceInfo device : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "读取音频输出设备失败", e);
        }
        return false;
    }

    /** 当前输出设备的名字，用于日志与排查（如 "AirPods Pro" / "本机扬声器"）。 */
    public String getOutputLabel() {
        if (!isBluetoothOutput()) {
            return "本机扬声器";
        }
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                for (AudioDeviceInfo device : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    int type = device.getType();
                    boolean isBluetoothDevice = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            || (Build.VERSION.SDK_INT >= 31
                                && type == AudioDeviceInfo.TYPE_BLE_HEADSET);
                    if (isBluetoothDevice) {
                        CharSequence name = device.getProductName();
                        if (name != null && name.length() > 0) {
                            return "蓝牙耳机 " + name;
                        }
                        return "蓝牙耳机";
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "蓝牙耳机";
    }

    public void addOutputChangeListener(OutputChangeListener listener) {
        if (listener != null && !outputListeners.contains(listener)) {
            outputListeners.add(listener);
        }
    }

    /**
     * 蓝牙耳机接入时的处理。
     * 用户要求不再弹出"已连接蓝牙耳机，音量与手表共用同一套 N 级"通知，
     * 故这里不再显示 Toast；输出路由变化仍由 onAudioRouteMaybeChanged 负责刷新。
     */
    public void notifyBluetoothConnectedOnce() {
        // 不再弹任何通知（用户明确要求）。
    }

    private void registerAudioDeviceCallback() {
        if (deviceCallbackRegistered) {
            return;
        }
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            lastBluetoothOutput = isBluetoothOutput();
            am.registerAudioDeviceCallback(new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    onAudioRouteMaybeChanged();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    onAudioRouteMaybeChanged();
                }
            }, mainHandler);
            deviceCallbackRegistered = true;
        } catch (Exception e) {
            Log.w(TAG, "注册音频设备监听失败", e);
        }
    }

    private void onAudioRouteMaybeChanged() {
        boolean bluetoothNow;
        try {
            bluetoothNow = isBluetoothOutput();
        } catch (Exception e) {
            return;
        }
        final boolean routeChanged = bluetoothNow != lastBluetoothOutput;
        // 两个蓝牙耳机之间互切时布尔值不变，但同样需要重新对齐音量
        if (!routeChanged && !bluetoothNow) {
            return;
        }
        lastBluetoothOutput = bluetoothNow;
        // 切换输出后，耳柄方向基线失效，下次以新系统音量重新建立，避免方向判断错位
        lastBtSystemVolume = -1;
        Log.i(TAG, "音频输出切换 -> " + (bluetoothNow ? "蓝牙耳机" : "本机输出")
                + "（" + getOutputLabel() + "），等级保持 " + level + "/" + MAX_LEVEL);
        if (bluetoothNow) {
            notifyBluetoothConnectedOnce();
        }
        // 蓝牙绝对音量协商常会顺手改系统 STREAM_MUSIC，立刻拉回锚点
        refreshGain();
        for (OutputChangeListener listener : new ArrayList<>(outputListeners)) {
            try {
                listener.onOutputChanged(bluetoothNow);
            } catch (Exception e) {
                Log.w(TAG, "输出变化回调异常", e);
            }
        }
    }

    private int levelFromSystemVolume() {
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return MAX_LEVEL;
            }
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) {
                return MAX_LEVEL;
            }
            int converted = Math.round(current * (float) MAX_LEVEL / max);
            return Math.max(0, Math.min(MAX_LEVEL, converted));
        } catch (Exception e) {
            return MAX_LEVEL;
        }
    }
}
