// fx_filter: DFM1 filter plugin for sixolet's fx mod framework.
// Lives in the sclang class library (everything under ~/dust is compiled
// as classes), so this file must only contain a class definition.
//
// Parameter smoothing, dual-stage tanh drive (pre or post filter),
// stereo cutoff spread, input envelope follower and resettable LFO on cutoff
// (free-running or clock-synced from Lua), output gain and limiter, DC leakage protection.

FxFilter : FxBase {
    *new {
        var ret = super.newCopyArgs(nil, \none, (
            cutoff: 1000,
            res: 0.1,
            dfm_gain: 1.0,
            type: 1,        // Lua option index, 1 = lowpass, 2 = highpass
            noise: 0.0003,
            drive_mode: 1,  // Lua option index, 1 = off, 2 = pre, 3 = post
            drive_amount: 1.0,
            stereo_spread: 0.0,
            env_amount: 0.0,
            env_sens: 2.0,
            env_attack: 0.01,
            env_release: 0.2,
            lfo_shape: 1,   // Lua option index: sine, tri, saw, square, s&h, noise
            lfo_rate: 1.0,
            lfo_depth: 0.0,
            lfo_reset: 0,   // trigger: restart LFO phase (sent on each synced cycle)
            out_gain: 0.0,  // dB
            limiter: 3      // Lua option index, 1 = off, 2 = soft clip, 3 = limiter
        ), nil, 1);
        ^ret;
    }

    *initClass {
        FxSetup.register(this.new);
    }

    subPath {
        ^"/fx_filter";
    }

    symbol {
        ^\fxFilter;
    }

    addSynthdefs {
        SynthDef(\fxFilter, { |inBus, outBus|
            var inSig, procL, procR, filtSig;

            // Lua options are 1-indexed; DFM1 type and drive mode are 0-indexed.
            var type       = \type.kr(1) - 1;
            var driveMode  = \drive_mode.kr(1) - 1;
            var limMode    = \limiter.kr(3) - 1;

            // Smooth continuous params to kill zipper noise.
            var cutoff  = Lag.kr(\cutoff.kr(1000).clip(20, 20000), 0.03);
            var res     = Lag.kr(\res.kr(0.1).clip(0.0, 1.2), 0.03);
            var dfmGain = Lag.kr(\dfm_gain.kr(1.0).clip(0.1, 10.0), 0.03);
            var noise   = \noise.kr(0.0003).clip(0.0, 0.01);
            var drive   = Lag.kr(\drive_amount.kr(1.0).clip(1.0, 10.0), 0.03);
            var spread  = Lag.kr(\stereo_spread.kr(0.0).clip(0.0, 1.0), 0.03);
            var outGain = Lag.kr(\out_gain.kr(0.0).clip(-24.0, 12.0).dbamp, 0.03);

            // Envelope follower on the input, modulates cutoff in octaves.
            var envAmt  = Lag.kr(\env_amount.kr(0.0).clip(-4.0, 4.0), 0.03);
            var envSens = Lag.kr(\env_sens.kr(2.0).clip(0.0, 20.0), 0.03);
            var envAtk  = \env_attack.kr(0.01).clip(0.001, 1.0);
            var envRel  = \env_release.kr(0.2).clip(0.01, 2.0);

            // LFO on cutoff, bipolar, depth in octaves.
            // Built from a resettable phasor so Lua can align it to the clock.
            var lfoShape = \lfo_shape.kr(1) - 1;
            var lfoRate  = Lag.kr(\lfo_rate.kr(1.0).clip(0.01, 20.0), 0.03);
            var lfoDepth = Lag.kr(\lfo_depth.kr(0.0).clip(-4.0, 4.0), 0.03);
            var lfoReset = \lfo_reset.tr(0);
            var phase, wrap, env, lfo, freqL, freqR, driveComp;

            inSig = In.ar(inBus, 2);

            env = Amplitude.kr(Mix.ar(inSig) * 0.5 * envSens, envAtk, envRel).clip(0.0, 1.0);

            // 0..1 ramp, one cycle per LFO period, jumps back to 0 on reset.
            phase = Phasor.kr(lfoReset, lfoRate / ControlRate.ir, 0, 1, 0);
            // Fires when the phasor wraps or is reset; clocks the s&h stage.
            wrap  = (HPZ1.kr(phase) < 0) + lfoReset;

            lfo = Select.kr(lfoShape, [
                sin(phase * 2pi),                                  // sine, starts at 0 rising
                1 - (4 * (((phase + 0.25) % 1) - 0.5).abs),        // triangle, phase-aligned to sine
                (phase * 2) - 1,                                   // saw, ramps -1 -> 1 each cycle
                ((phase < 0.5) * 2) - 1,                           // square, high first half
                Latch.kr(WhiteNoise.kr, wrap),                     // stepped sample & hold
                LFNoise2.kr(lfoRate)                               // smooth noise (not phase-locked)
            ]);
            // Light lag so square and s&h steps don't click the filter.
            lfo = Lag.kr(lfo, 0.005);

            cutoff = (cutoff * (2 ** ((env * envAmt) + (lfo * lfoDepth)))).clip(20, 20000);

            // Offset L/R cutoff by spread.
            freqL = (cutoff * (1 - (spread * 0.35))).clip(20, 20000);
            freqR = (cutoff * (1 + (spread * 0.35))).clip(20, 20000);

            // Level compensation for tanh drive.
            driveComp = 1 / drive.sqrt;

            // Pre-filter drive.
            procL = Select.ar(driveMode >= 1, [inSig[0], (inSig[0] * drive).tanh * driveComp]);
            procR = Select.ar(driveMode >= 1, [inSig[1], (inSig[1] * drive).tanh * driveComp]);

            procL = DFM1.ar(procL, freqL, res, dfmGain, type, noise);
            procR = DFM1.ar(procR, freqR, res, dfmGain, type, noise);

            // Post-filter drive, tames self-oscillation peaks.
            procL = Select.ar(driveMode >= 2, [procL, (procL * drive).tanh * driveComp]);
            procR = Select.ar(driveMode >= 2, [procR, (procR * drive).tanh * driveComp]);

            filtSig = LeakDC.ar([procL, procR]) * outGain;

            // Output stage: off, zero-latency soft clip, or lookahead limiter (2 ms).
            filtSig = filtSig.collect { |ch|
                Select.ar(limMode, [ch, ch.tanh, Limiter.ar(ch, 0.95, 0.002)]);
            };

            // Dry/wet handled by the fx framework's replacer on the insert slot.
            Out.ar(outBus, filtSig);
        }).add;
    }
}
