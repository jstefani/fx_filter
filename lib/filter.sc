// fx_filter: multimode filter plugin for sixolet's fx mod framework.
// Lives in the sclang class library (everything under ~/dust is compiled
// as classes), so this file must only contain a class definition.
//
// Five filter models (DFM1, MoogFF, SVF, RLPF, BMoog), each its own SynthDef
// built by one shared function. Lowpass / highpass / bandpass / notch via a
// two-stage chain with a width param. Drive with five shapers and a tilt tone,
// pre or post filter. Envelope follower (source, threshold, invert) and a
// resettable stereo LFO (phase offset, polarity, clock sync from Lua)
// modulating cutoff, resonance and drive. Output gain, limiter, DC protection.

FxFilter : FxBase {
    classvar <modelSymbols;

    var <>modelIndex;

    *new {
        var ret = super.newCopyArgs(nil, \none, (
            cutoff: 1000,
            res: 0.1,
            dfm_gain: 1.0,
            type: 1,          // Lua option index: lowpass, highpass, bandpass, notch
            width: 1.0,       // bandpass / notch width in octaves
            noise: 0.0003,
            drive_mode: 1,    // Lua option index: off, pre, post
            drive_type: 1,    // Lua option index: tanh, soft, hard, asym, fold
            drive_amount: 1.0,
            drive_tone: 0.0,  // -1 dark .. 1 bright, tilt after the shaper
            stereo_spread: 0.0,
            env_amount: 0.0,
            env_res: 0.0,
            env_drive: 0.0,
            env_source: 1,    // Lua option index: sum, left, right
            env_threshold: 0.0,
            env_polarity: 1,  // Lua option index: normal, inverted
            env_sens: 2.0,
            env_attack: 0.01,
            env_release: 0.2,
            lfo_shape: 1,     // Lua option index: sine, tri, saw, square, s&h, noise
            lfo_rate: 1.0,
            lfo_depth: 0.0,
            lfo_res: 0.0,
            lfo_phase: 0.0,   // degrees, R channel offset from L
            lfo_polarity: 1,  // Lua option index: bipolar, unipolar
            lfo_reset: 0,     // trigger: restart LFO phase (sent on each synced cycle)
            out_gain: 0.0,    // dB
            limiter: 3        // Lua option index: off, soft clip, limiter
        ), nil, 1);
        ret.modelIndex = 1;
        ^ret;
    }

    *initClass {
        modelSymbols = [\fxFilterDfm1, \fxFilterMoog, \fxFilterSvf, \fxFilterRlpf, \fxFilterBmoog];
        FxSetup.register(this.new);
    }

    subPath {
        ^"/fx_filter";
    }

    // FxBase creates the synth from this, so it follows the selected model.
    symbol {
        ^modelSymbols.clipAt(modelIndex - 1);
    }

    listenOSC {
        super.listenOSC;
        // Model change: tear the synth down and rebuild it on the same slot
        // with the stored params. Brief dropout, acceptable for a model switch.
        OSCFunc.new({ |msg, time, addr, recvPort|
            var current = slot;
            modelIndex = msg[1].asInteger;
            this.handleSlot(\none);
            this.handleSlot(current);
        }, this.subPath ++ "/model");
    }

    addSynthdefs {
        // Each core: { |sig, freq, res, type, dfmGain, noise| ... }
        // sig audio, freq Hz (kr), res 0..1.2 (kr), type 0 = lowpass 1 = highpass (kr).

        this.makeDef(\fxFilterDfm1, { |sig, freq, res, type, dfmGain, noise|
            DFM1.ar(sig, freq, res, dfmGain, type, noise);
        });

        this.makeDef(\fxFilterMoog, { |sig, freq, res, type|
            var lp = MoogFF.ar(sig, freq, res * 3.3);
            Select.ar(type, [lp, sig - lp]);
        });

        this.makeDef(\fxFilterSvf, { |sig, freq, res, type|
            SVF.ar(sig, freq, (res / 1.2).clip(0, 0.98), 1 - type, 0, type);
        });

        this.makeDef(\fxFilterRlpf, { |sig, freq, res, type|
            var rq = 1 / (1 + (res * 15));
            Select.ar(type, [RLPF.ar(sig, freq, rq), RHPF.ar(sig, freq, rq)]);
        });

        this.makeDef(\fxFilterBmoog, { |sig, freq, res, type|
            BMoog.ar(sig, freq, (res / 1.2).clip(0, 0.98), type, 0.95);
        });
    }

    makeDef { |name, core|
        SynthDef(name, { |inBus, outBus|
            var inSig, envSrc, env, phaseL, phaseR, noiseL, noiseR, lfoL, lfoR;
            var driveAmt, driveComp, procL, procR, filtSig;
            var shape, lfoFrom, stage, chain, process;

            // Lua options are 1-indexed; everything downstream is 0-indexed.
            var mode        = \type.kr(1) - 1;
            var driveMode   = \drive_mode.kr(1) - 1;
            var driveType   = \drive_type.kr(1) - 1;
            var envSource   = \env_source.kr(1) - 1;
            var envInvert   = \env_polarity.kr(1) - 1;
            var lfoShape    = \lfo_shape.kr(1) - 1;
            var lfoUnipolar = \lfo_polarity.kr(1) - 1;
            var limMode     = \limiter.kr(3) - 1;

            // Smooth continuous params to kill zipper noise.
            var cutoff  = Lag.kr(\cutoff.kr(1000).clip(20, 20000), 0.03);
            var res     = Lag.kr(\res.kr(0.1).clip(0.0, 1.2), 0.03);
            var width   = Lag.kr(\width.kr(1.0).clip(0.1, 4.0), 0.03);
            var dfmGain = Lag.kr(\dfm_gain.kr(1.0).clip(0.1, 10.0), 0.03);
            var noise   = \noise.kr(0.0003).clip(0.0, 0.01);
            var drive   = Lag.kr(\drive_amount.kr(1.0).clip(1.0, 10.0), 0.03);
            var toneDb  = Lag.kr(\drive_tone.kr(0.0).clip(-1.0, 1.0), 0.03) * 6;
            var spread  = Lag.kr(\stereo_spread.kr(0.0).clip(0.0, 1.0), 0.03);
            var outGain = Lag.kr(\out_gain.kr(0.0).clip(-24.0, 12.0).dbamp, 0.03);

            // Envelope follower on the input.
            var envAmt   = Lag.kr(\env_amount.kr(0.0).clip(-4.0, 4.0), 0.03);
            var envRes   = Lag.kr(\env_res.kr(0.0).clip(-1.0, 1.0), 0.03);
            var envDrive = Lag.kr(\env_drive.kr(0.0).clip(0.0, 8.0), 0.03);
            var envThr   = Lag.kr(\env_threshold.kr(0.0).clip(0.0, 0.95), 0.03);
            var envSens  = Lag.kr(\env_sens.kr(2.0).clip(0.0, 20.0), 0.03);
            var envAtk   = \env_attack.kr(0.01).clip(0.001, 1.0);
            var envRel   = \env_release.kr(0.2).clip(0.01, 2.0);

            // LFO, built from a resettable phasor so Lua can align it to the clock.
            var lfoRate  = Lag.kr(\lfo_rate.kr(1.0).clip(0.01, 20.0), 0.03);
            var lfoDepth = Lag.kr(\lfo_depth.kr(0.0).clip(-4.0, 4.0), 0.03);
            var lfoRes   = Lag.kr(\lfo_res.kr(0.0).clip(-1.0, 1.0), 0.03);
            var lfoPhase = Lag.kr(\lfo_phase.kr(0.0).clip(0.0, 180.0), 0.03);
            var lfoReset = \lfo_reset.tr(0);

            inSig = In.ar(inBus, 2);

            // ---- envelope follower --------------------------------------
            envSrc = Select.ar(envSource, [Mix.ar(inSig) * 0.5, inSig[0], inSig[1]]);
            env = A2K.kr(Amplitude.ar(envSrc * envSens, envAtk, envRel)).clip(0.0, 1.0);
            // Floor: nothing below threshold, rescaled so the top still hits 1.
            env = ((env - envThr) / (1 - envThr)).clip(0.0, 1.0);
            env = Select.kr(envInvert, [env, 1 - env]);

            // ---- lfo ---------------------------------------------------
            // 0..1 ramp, one cycle per period, jumps back to 0 on reset.
            phaseL = Phasor.kr(lfoReset, lfoRate / ControlRate.ir, 0, 1, 0);
            phaseR = (phaseL + (lfoPhase / 360)) % 1;
            // Smooth noise isn't phase-locked; blend toward an independent
            // generator as the offset grows.
            noiseL = LFNoise2.kr(lfoRate);
            noiseR = LinXFade2.kr(noiseL, LFNoise2.kr(lfoRate), (lfoPhase / 90) - 1);

            lfoFrom = { |ph, noiseSig|
                // Fires when the phasor wraps or is reset; clocks the s&h stage.
                // Wrap = drop of more than half a cycle, so a slowly falling
                // stereo phase offset doesn't retrigger the right channel.
                var wrap = ((Delay1.kr(ph) - ph) > 0.5) + lfoReset;
                var out = Select.kr(lfoShape, [
                    sin(ph * 2pi),                                 // sine, starts at 0 rising
                    1 - (4 * (((ph + 0.25) % 1) - 0.5).abs),       // triangle, aligned to sine
                    (ph * 2) - 1,                                  // saw, ramps -1 -> 1
                    ((ph < 0.5) * 2) - 1,                          // square, high first half
                    Latch.kr(WhiteNoise.kr, wrap),                 // stepped sample & hold
                    noiseSig                                       // smooth noise
                ]);
                // Light lag so square and s&h steps don't click the filter.
                out = Lag.kr(out, 0.005);
                Select.kr(lfoUnipolar, [out, (out + 1) * 0.5]);
            };
            lfoL = lfoFrom.(phaseL, noiseL);
            lfoR = lfoFrom.(phaseR, noiseR);

            // ---- drive -------------------------------------------------
            driveAmt  = (drive + (env * envDrive)).clip(1.0, 10.0);
            driveComp = 1 / driveAmt.sqrt;   // level compensation

            shape = { |sig|
                var x = sig * driveAmt;
                var y = Select.ar(driveType, [
                    x.tanh,
                    x.softclip,
                    x.clip2(1),
                    (x + 0.5).tanh - 0.5.tanh,   // asymmetric, even harmonics
                    x.fold2(1)                   // wavefold
                ]) * driveComp;
                // Tilt around 1 kHz: negative = darker, positive = brighter.
                y = BLowShelf.ar(y, 1000, 1, toneDb.neg);
                BHiShelf.ar(y, 1000, 1, toneDb);
            };

            // ---- filter chain ------------------------------------------
            stage = { |sig, freq, r, type|
                core.value(sig, freq, r, type, dfmGain, noise);
            };

            // lowpass:  A = lp(f)                      out = A
            // highpass: A = hp(f)                      out = A
            // bandpass: A = hp(fLo), B = lp(fHi, A)    out = B   (series)
            // notch:    A = lp(fLo), B = hp(fHi, sig)  out = A+B (parallel)
            chain = { |sig, freq, r|
                var fLo = (freq * (2 ** (width * -0.5))).clip(20, 18000);
                var fHi = (freq * (2 ** (width * 0.5))).clip(20, 18000);
                var fA = Select.kr(mode, [freq, freq, fLo, fLo]);
                var fB = Select.kr(mode, [freq, freq, fHi, fHi]);
                var tA = Select.kr(mode, [0, 1, 1, 0]);
                var tB = Select.kr(mode, [0, 1, 0, 1]);
                var a = stage.(sig, fA, r, tA);
                var b = stage.(Select.ar(mode >= 3, [a, sig]), fB, r, tB);
                Select.ar(mode, [a, a, b, a + b]);
            };

            // ---- per channel -------------------------------------------
            process = { |sig, lfo, side|
                var freq = cutoff * (2 ** ((env * envAmt) + (lfo * lfoDepth)));
                var r = (res + (env * envRes) + (lfo * lfoRes)).clip(0.0, 1.2);
                var pre, filt;
                // Offset L/R cutoff by spread.
                freq = (freq * (1 + (side * spread * 0.35))).clip(20, 18000);
                pre  = Select.ar(driveMode >= 1, [sig, shape.(sig)]);
                filt = chain.(pre, freq, r);
                // Post-filter drive tames self-oscillation peaks.
                Select.ar(driveMode >= 2, [filt, shape.(filt)]);
            };

            procL = process.(inSig[0], lfoL, -1);
            procR = process.(inSig[1], lfoR, 1);

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
