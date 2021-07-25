package cc.craftospc.AdvancedSpeaker;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Optional;

public class AdvancedSpeakerTileEntity extends TileEntity implements ITickableTileEntity {
    enum WaveType {
        None,
        Sine,
        Triangle,
        Sawtooth,
        RSawtooth,
        Square,
        Noise
    };

    public static final class Peripheral implements IPeripheral {
        private static class Channel {
            int channelNumber;
            double position = 0.0;
            WaveType wavetype = WaveType.None;
            double duty = 0.5;
            int frequency = 0;
            double amplitude = 1.0;
            double pan = 0.0;
            int fadeSamples = 0;
            int fadeSamplesMax = 0;
            double fadeSamplesInit = 0.0;
            boolean halting = false;
            int channelCount = 4;
        }

        private static final int BUFFER_SIZE = 1024;
        private static final int BUFFER_COUNT = 8;

        private final Channel[] channels = new Channel[8];
        private final IntBuffer buffers;
        private final int source;
        public final float maxDistance = 32;

        public Peripheral(AdvancedSpeakerTileEntity speaker) {
            for (int i = 0; i < 8; i++) channels[i] = new Channel();

            buffers = BufferUtils.createIntBuffer(BUFFER_COUNT);
            AL10.alGenBuffers(buffers);
            ShortBuffer[] bufferData = new ShortBuffer[BUFFER_COUNT];
            for (int i = 0; i < BUFFER_COUNT; i++) {
                bufferData[i] = BufferUtils.createShortBuffer(BUFFER_SIZE);
                AL10.alBufferData(buffers.get(i), AL10.AL_FORMAT_MONO16, bufferData[i], 48000);
            }

            source = AL10.alGenSources();
            AL10.alSource3f(source, AL10.AL_POSITION, speaker.getBlockPos().getX() + 0.5f, speaker.getBlockPos().getY() + 0.5f, speaker.getBlockPos().getZ() + 0.5f);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, (24F * 0.25F) / maxDistance);

            //fun stuff
            AL10.alSourcef(source, AL10.AL_GAIN, 1f);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);

            AL10.alSourceQueueBuffers(source, buffers);

            //Trigger the source to play its sound
            AL10.alSourcePlay(source);
            AdvancedSpeaker.LOGGER.info("Started speaker");
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            AL10.alSourceStop(source); //Demand that the sound stop

            //and finally, clean up
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffers);
        }

        private static double getSample(WaveType type, double amplitude, double pos, double duty) {
            if (amplitude < 0.0001) return 0.0;
            switch (type) {
                case Sine: return amplitude * Math.sin(2.0 * pos * Math.PI);
                case Triangle: return 2.0 * Math.abs(amplitude * ((2.0 * pos + 1.5) % 2.0) - amplitude) - amplitude;
                case Sawtooth: return amplitude * ((2.0 * pos + 1.0) % 2.0) - amplitude;
                case RSawtooth: return amplitude * ((2.0 * (1.0 - pos) + 1.0) % 2.0) - amplitude;
                case Square:
                    if (pos >= duty) return -amplitude;
                    else return amplitude;
                case Noise: return amplitude * (Math.random() * 2.0 - 1.0);
                default: return 0.0;
            }
        }

        private void generateWaveform(ShortBuffer stream) {
            for (int i = 0; i < stream.capacity(); i++) {
                double sample = 0;
                for (int j = 0; j < channels.length; j++) {
                    Channel info = channels[j];
                    sample += info.frequency == 0 ? 0.0 : getSample(info.wavetype, info.amplitude, info.position, info.duty);
                    info.position = info.position + (double)info.frequency / 48000;
                    while (info.position >= 1.0) info.position -= 1.0;
                    if (info.fadeSamplesMax > 0) {
                        info.amplitude -= info.fadeSamplesInit / info.fadeSamplesMax;
                        if (--info.fadeSamples <= 0) {
                            info.fadeSamples = info.fadeSamplesMax = 0;
                            info.fadeSamplesInit = info.amplitude = 0.0f;
                        }
                    }
                }
                stream.put(i, (short)((sample / channels.length) * 32767));
            }
        }

        public void update() {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AdvancedSpeaker.LOGGER.debug("Speaker stopped playing! Is the server overloaded?");
                AL10.alSourcePlay(source);
                return;
            }
            int buffersProcessed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            while (buffersProcessed-- > 0) {
                int buffer = AL10.alSourceUnqueueBuffers(source);
                ShortBuffer stream = BufferUtils.createShortBuffer(BUFFER_SIZE);
                generateWaveform(stream);
                AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, stream, 48000);
                AL10.alSourceQueueBuffers(source, buffer);
            }
        }

        public void setPosition(BlockPos pos) {
            AL10.alSource3f(source, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
        }

        @Nonnull
        @Override
        public String getType() {
            return "advanced_speaker";
        }

        @Override
        public boolean equals(@Nullable IPeripheral iPeripheral) {
            return iPeripheral instanceof Peripheral;
        }

        @LuaFunction
        public final String getWaveType(int channel) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            switch (info.wavetype) {
                case None: return "none";
                case Sine: return "sine";
                case Triangle: return "triangle";
                case Sawtooth: return "sawtooth";
                case RSawtooth: return "rsawtooth";
                case Square: return "square";
                case Noise: return "noise";
                default: return "unknown";
            }
        }

        @LuaFunction
        public final void setWaveType(int channel, String type, Optional<Double> duty) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            switch (type) {
                case "none":
                    info.wavetype = WaveType.None;
                    break;
                case "sine":
                    info.wavetype = WaveType.Sine;
                    break;
                case "triangle":
                    info.wavetype = WaveType.Triangle;
                    break;
                case "sawtooth":
                    info.wavetype = WaveType.Sawtooth;
                    break;
                case "rsawtooth":
                    info.wavetype = WaveType.RSawtooth;
                    break;
                case "square":
                    if (duty.isPresent()) {
                        double d = duty.get();
                        if (d < 0 || d > 1) throw new LuaException("bad argument #3 (value out of range)");
                        info.duty = d;
                    } else info.duty = 0.5;
                    info.wavetype = WaveType.Square;
                    break;
                case "noise":
                    info.wavetype = WaveType.Noise;
                    break;
                default:
                    throw new LuaException("bad argument #2 (invalid option '" + type + "')");
            }
        }

        @LuaFunction
        public final int getFrequency(int channel) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            return info.frequency;
        }

        @LuaFunction
        public final void setFrequency(int channel, int frequency) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            if (frequency < 0) throw new LuaException("bad argument #2 (value out of range)");
            info.frequency = frequency;
        }

        @LuaFunction
        public final double getVolume(int channel) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            return info.amplitude;
        }

        @LuaFunction
        public final void setVolume(int channel, double volume) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            if (volume < 0 || volume > 1) throw new LuaException("bad argument #2 (value out of range)");
            info.amplitude = volume;
        }

        @LuaFunction
        public final double getPan(int channel) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            return info.pan;
        }

        @LuaFunction
        public final void setPan(int channel, double pan) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            if (pan < -1 || pan > 1) throw new LuaException("bad argument #2 (value out of range)");
            info.pan = pan;
        }

        @LuaFunction
        public final void fadeOut(int channel, double time) throws LuaException {
            update();
            if (channel < 1 || channel > channels.length) throw new LuaException("bad argument #1 (channel out of range)");
            Channel info = channels[channel-1];
            if (time < 0.0001) {
                info.fadeSamplesInit = 0.0;
                info.fadeSamples = info.fadeSamplesMax = 0;
            } else {
                info.fadeSamplesInit = info.amplitude;
                info.fadeSamples = info.fadeSamplesMax = (int)(time * 48000);
            }
        }
    }

    Peripheral peripheral = new Peripheral(this);

    public AdvancedSpeakerTileEntity() {
        super(AdvancedSpeaker.ADVANCED_SPEAKER_TE.get());
    }

    @Override
    public void tick() {
        peripheral.update();
    }

    @Override
    public void setPosition(BlockPos pos) {
        super.setPosition(pos);
        peripheral.setPosition(pos);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        peripheral.setPosition(worldPosition);
    }
}
