package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Plays audio files from external folder when you sneak OR on a random timer
 *
 * Folder: %APPDATA%\.minecraft\meteor-client\hybridious_mod\
 *
 * Author: Hybridious
 */
public class SoundOnSneak extends Module {
    private boolean wasSneaking = false;
    private Clip audioClip = null;
    private final Random random = new Random();
    private boolean initialized = false;
    private Thread audioThread = null;

    // Debounce variables to prevent spam
    private long lastSneakPlayTime = 0;
    private static final long SNEAK_COOLDOWN = 500; // 500ms cooldown between sneak sounds

    // Timer variables
    private long nextPlayTime = 0;

    // DON'T use static final - lazy load this to avoid network errors
    private Path soundDir = null;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTimer = settings.createGroup("Timer");

    // General Settings
    private final Setting<Boolean> enableSneak = sgGeneral.add(new BoolSetting.Builder()
            .name("enable-sneak")
            .description("Play sounds when you sneak")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> randomSound = sgGeneral.add(new BoolSetting.Builder()
            .name("random-sound")
            .description("Pick a random sound from the folder instead of using sound-file setting")
            .defaultValue(false)
            .build()
    );

    private final Setting<String> soundFile = sgGeneral.add(new StringSetting.Builder()
            .name("sound-file")
            .description("Name of the audio file to play (WAV recommended). Ignored if random-sound is enabled.")
            .defaultValue("sound.wav")
            .visible(() -> !randomSound.get())
            .build()
    );

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
            .name("volume")
            .description("Volume of the sound (0.0 to 1.0)")
            .defaultValue(0.5)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.0, 1.0)
            .build()
    );

    private final Setting<Boolean> stopOnRelease = sgGeneral.add(new BoolSetting.Builder()
            .name("stop-on-release")
            .description("Stop the sound when you stop sneaking")
            .defaultValue(false)
            .visible(() -> enableSneak.get())
            .build()
    );

    private final Setting<Boolean> loopSound = sgGeneral.add(new BoolSetting.Builder()
            .name("loop-sound")
            .description("Loop the sound continuously while sneaking")
            .defaultValue(false)
            .visible(() -> enableSneak.get())
            .build()
    );

    // Timer Settings
    private final Setting<Boolean> enableTimer = sgTimer.add(new BoolSetting.Builder()
            .name("enable-timer")
            .description("Automatically play sounds on a random timer interval")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> minMinutes = sgTimer.add(new IntSetting.Builder()
            .name("min-minutes")
            .description("Minimum minutes between automatic sounds")
            .defaultValue(5)
            .min(1)
            .max(60)
            .sliderRange(1, 60)
            .visible(() -> enableTimer.get())
            .build()
    );

    private final Setting<Integer> maxMinutes = sgTimer.add(new IntSetting.Builder()
            .name("max-minutes")
            .description("Maximum minutes between automatic sounds")
            .defaultValue(10)
            .min(1)
            .max(60)
            .sliderRange(1, 60)
            .visible(() -> enableTimer.get())
            .build()
    );

    public SoundOnSneak() {
        super(Hybridious.CATEGORY, "sound-on-sneak", "Plays audio files when you sneak or on a random timer. Created for FartClan on 2b2t.");
        // Don't initialize anything here
    }

    private Path getSoundDir() {
        if (soundDir == null) {
            soundDir = MeteorClient.FOLDER.toPath().resolve("hybridious_mod");
        }
        return soundDir;
    }

    private void initializeSoundDirectory() {
        if (initialized) return;
        initialized = true;

        try {
            Path dir = getSoundDir();

            // Create the directory if it doesn't exist
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    private List<String> findAudioFiles() {
        List<String> audioFiles = new ArrayList<>();
        try {
            Path dir = getSoundDir();
            if (Files.exists(dir)) {
                Files.list(dir)
                        .filter(path -> {
                            String name = path.toString().toLowerCase();
                            return name.endsWith(".wav") || name.endsWith(".ogg") ||
                                    name.endsWith(".mp3") || name.endsWith(".aiff") ||
                                    name.endsWith(".au");
                        })
                        .forEach(path -> audioFiles.add(path.getFileName().toString()));
            }
        } catch (Exception e) {
            // Silently fail during scan
        }
        return audioFiles;
    }

    private List<String> findSupportedAudioFiles() {
        List<String> audioFiles = new ArrayList<>();
        try {
            Path dir = getSoundDir();
            if (Files.exists(dir)) {
                Files.list(dir)
                        .filter(path -> {
                            String name = path.toString().toLowerCase();
                            // Only return actually supported formats
                            return name.endsWith(".wav") || name.endsWith(".aiff") || name.endsWith(".au");
                        })
                        .forEach(path -> audioFiles.add(path.getFileName().toString()));
            }
        } catch (Exception e) {
            // Silently fail during scan
        }
        return audioFiles;
    }

    private String getRandomSoundFile() {
        List<String> supportedFiles = findSupportedAudioFiles();
        if (supportedFiles.isEmpty()) {
            return null;
        }
        return supportedFiles.get(random.nextInt(supportedFiles.size()));
    }

    private long getRandomInterval() {
        // Get random interval between min and max minutes
        int min = Math.min(minMinutes.get(), maxMinutes.get());
        int max = Math.max(minMinutes.get(), maxMinutes.get());

        int randomMinutes = min + random.nextInt(max - min + 1);
        return randomMinutes * 60 * 1000L; // Convert to milliseconds
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean isSneaking = mc.player.isSneaking();

        // Check timer
        if (enableTimer.get()) {
            long currentTime = System.currentTimeMillis();

            // Initialize next play time if not set
            if (nextPlayTime == 0) {
                nextPlayTime = currentTime + getRandomInterval();
            }

            // Check if it's time to play
            if (currentTime >= nextPlayTime) {
                playSound("Timer");
                // Set next random interval
                nextPlayTime = currentTime + getRandomInterval();
            }
        }

        // Detect sneak key press with cooldown (only if sneak is enabled)
        if (enableSneak.get()) {
            if (isSneaking && !wasSneaking) {
                long currentTime = System.currentTimeMillis();
                // Only play if cooldown has passed
                if (currentTime - lastSneakPlayTime >= SNEAK_COOLDOWN) {
                    playSound("Sneak");
                    lastSneakPlayTime = currentTime;
                }
            }

            // Stop sound when releasing sneak (if enabled)
            if (!isSneaking && wasSneaking && stopOnRelease.get()) {
                stopSound();
            }
        }

        wasSneaking = isSneaking;
    }

    private void playSound(String trigger) {
        // Stop any currently playing sound and thread
        stopSound();

        try {
            // Determine which file to play
            String fileToPlay;
            if (randomSound.get()) {
                fileToPlay = getRandomSoundFile();
                if (fileToPlay == null) {
                    return; // Silently fail
                }
            } else {
                fileToPlay = soundFile.get();
            }

            Path soundPath = getSoundDir().resolve(fileToPlay);

            // Check if the sound file exists
            if (!Files.exists(soundPath)) {
                return; // Silently fail
            }

            // Check file extension and warn if not WAV
            String fileName = fileToPlay.toLowerCase();
            if (!fileName.endsWith(".wav") && !fileName.endsWith(".aiff") && !fileName.endsWith(".au")) {
                return; // Silently fail
            }

            final String finalFileToPlay = fileToPlay;
            final String finalTrigger = trigger;

            // Create and start audio thread
            audioThread = new Thread(() -> {
                try {
                    AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundPath.toFile());
                    AudioFormat format = audioInputStream.getFormat();

                    audioClip = AudioSystem.getClip();
                    audioClip.open(audioInputStream);

                    // Set volume control
                    if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gainControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = (float) (Math.log(Math.max(0.0001, volume.get())) / Math.log(10.0) * 20.0);
                        gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                    }

                    // Set looping mode (only for sneak trigger)
                    if (loopSound.get() && finalTrigger.equals("Sneak")) {
                        audioClip.loop(Clip.LOOP_CONTINUOUSLY);
                    } else {
                        audioClip.start();
                    }

                    // Wait for the clip to finish if not looping
                    if (!loopSound.get() || !finalTrigger.equals("Sneak")) {
                        while (audioClip != null && audioClip.isRunning()) {
                            Thread.sleep(100);
                        }
                    }

                } catch (InterruptedException e) {
                    // Thread was interrupted - clean up
                    if (audioClip != null) {
                        audioClip.stop();
                        audioClip.close();
                    }
                } catch (Exception e) {
                    // Silently fail
                }
            }, "Audio-Player");

            audioThread.setDaemon(true); // Make thread daemon so it doesn't prevent JVM shutdown
            audioThread.start();

        } catch (Exception e) {
            // Silently fail
        }
    }

    private void stopSound() {
        // Stop the clip
        if (audioClip != null) {
            try {
                audioClip.stop();
                audioClip.close();
            } catch (Exception e) {
                // Ignore errors when stopping
            }
            audioClip = null;
        }

        // Interrupt and clean up the thread
        if (audioThread != null && audioThread.isAlive()) {
            try {
                audioThread.interrupt();
                audioThread = null;
            } catch (Exception e) {
                // Ignore errors
            }
        }
    }

    @Override
    public void onActivate() {
        wasSneaking = false;
        nextPlayTime = 0;
        lastSneakPlayTime = 0;

        // Initialize directory on first activation (not during construction)
        initializeSoundDirectory();
    }

    @Override
    public void onDeactivate() {
        stopSound();
        wasSneaking = false;
        nextPlayTime = 0;
        lastSneakPlayTime = 0;
    }
}