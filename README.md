# fx_filter for norns

A stereo multimode filter as a norns effect, built on sixolet's [fx mod](https://llllllll.co/t/fx-mod/62726) framework. Runs on send A, send B, or as an insert with dry/wet, on top of any script. Started life as a wrapper around [DFM1](https://doc.sccode.org/Classes/DFM1.html), which is still the default model.

> DFM1 is a digitally modelled analog filter. It provides low-pass and high-pass filtering. The filter can be overdriven and will self-oscillate at high resonances.

Features:

- five filter models: DFM1, MoogFF, SVF, RLPF, BMoog
- lowpass / highpass / bandpass / notch with a width control, resonance into self-oscillation
- drive before or after the filter: tanh, soft, hard, asymmetric, wavefold, plus a tilt tone
- stereo cutoff spread
- input envelope follower with source, threshold and invert, to cutoff, resonance and drive
- stereo LFO with phase offset and polarity, free-running or synced to the norns clock, to cutoff and resonance
- output level with limiter or soft clip
- all params smoothed, MIDI-mappable, and saved with PSETs

## Requirements

- norns
- [fx mod](https://llllllll.co/t/fx-mod/62726), installed and enabled

## Installation

Install via [Maiden](https://norns.local):

```
;install https://github.com/xmacex/fx_filter
```

Enable it in `SYSTEM > MODS`, then restart norns.

## Usage

Parameters live in the norns `PARAMETERS` menu under `fx filter`. Pick a `slot` (send a, send b, insert) to hear it. Hold <kbd>K3</kbd> while turning <kbd>E3</kbd> for fine control.

See the [fx mod](https://llllllll.co/t/fx-mod/62726) docs for routing scripts into sends and end-of-chain behaviour.

### slot

| param | range | notes |
| --- | --- | --- |
| slot | none, send a, send b, insert | where the filter sits |
| dry/wet | 0..1 | insert only |

### filter

| param | range | default | notes |
| --- | --- | --- | --- |
| model | dfm1, moog ff, svf, rlpf, bmoog | dfm1 | see models below |
| cutoff | 20..20000 Hz | 1000 | base cutoff before modulation; stages clip at 18 kHz after modulation |
| resonance | 0..1.2 | 0.1 | mapped per model; DFM1 self-oscillates above ~1.0 |
| type | lowpass, highpass, bandpass, notch | lowpass | |
| width | 0.1..4 oct | 1 | bandpass / notch width, centred on cutoff |
| input gain | 0.1..8 | 1 | DFM1 only, pushes the filter core |
| stereo spread | 0..1 | 0 | offsets L/R cutoff, up to ±35% |
| noise | 0..0.005 | 0.0003 | DFM1 only, analog noise floor |

**Models.** Each is its own SynthDef, so only the selected one runs. Switching models rebuilds the synth and drops the wet signal for a moment.

| model | character | slope | source |
| --- | --- | --- | --- |
| dfm1 | analog-modelled ladder, overdrivable, self-oscillates | 24 dB | sc3-plugins |
| moog ff | Moog ladder, softer resonance; highpass is derived as input minus lowpass | 24 dB | SC core |
| svf | clean state-variable filter | 12 dB | sc3-plugins |
| rlpf | plain resonant biquad, cheapest | 12 dB | SC core |
| bmoog | Moog-style ladder with saturation | 24 dB | sc3-plugins |

**Bandpass and notch** are built from two stages of the chosen model. Bandpass is a highpass into a lowpass, spaced `width` octaves apart around cutoff. Notch is a lowpass and a highpass in parallel. Resonance applies to both stages, so high resonance on a narrow bandpass gets peaky.

### drive

| param | range | default | notes |
| --- | --- | --- | --- |
| drive mode | off, pre-filter, post-filter | off | post tames self-oscillation peaks |
| drive type | tanh, soft, hard, asym, fold | tanh | asym adds even harmonics, fold is a wavefolder |
| drive amount | 1..8 | 1 | level-compensated; at 1, hard and fold are clean |
| drive tone | -1..1 | 0 | tilt after the shaper, ±6 dB around 1 kHz, negative = darker |

### envelope

Envelope follower on the input signal. Detection runs at audio rate.

| param | range | default | notes |
| --- | --- | --- | --- |
| env > cutoff | -4..4 oct | 0 | negative values duck the cutoff |
| env > res | -1..1 | 0 | added to resonance |
| env > drive | 0..7 | 0 | added to drive amount, needs drive mode on |
| env source | sum, left, right | sum | sidechain from one channel |
| env threshold | 0..0.95 | 0 | floor; below it the envelope reads 0, above it rescales to 1 |
| env polarity | normal, inverted | normal | inverted = 1 minus envelope, for ducking |
| env sensitivity | 0..20 | 2 | input gain into the follower |
| env attack | 1 ms..1 s | 10 ms | |
| env release | 10 ms..2 s | 200 ms | |

### lfo

| param | range | default | notes |
| --- | --- | --- | --- |
| lfo shape | sine, triangle, saw, square, s&h, noise | sine | |
| lfo mode | free, sync | free | |
| lfo rate | 0.01..20 Hz | 1 Hz | shown in free mode |
| lfo division | 4 bars .. 1/32, dotted and triplet | 1/4 | shown in sync mode |
| lfo > cutoff | -127..127 | 0 | ±127 = ±4 octaves |
| lfo > res | -1..1 | 0 | added to resonance |
| lfo stereo phase | 0..180 deg | 0 | right channel LFO offset from left; 180 = opposite |
| lfo polarity | bipolar, unipolar | bipolar | unipolar runs 0..1, modulation only goes one way from cutoff |

In **sync** mode the rate follows the norns clock tempo and the LFO phase restarts on every division boundary, so it stays locked through tempo changes and transport jumps. Divisions assume 4/4. Saw ramps from bottom to top across each cycle, square is high for the first half, s&h steps on each cycle, noise is smooth and not phase-locked.

In **free** mode the LFO runs at the set rate and ignores the clock.

Stereo phase offsets the right channel's LFO along the same cycle, so a synced sine at 180° sweeps L and R in opposite directions on the beat. For the noise shape the right channel blends toward an independent generator as the offset grows.

### output

| param | range | default | notes |
| --- | --- | --- | --- |
| level | -24..12 dB | 0 dB | |
| limiter | off, soft clip, limiter | limiter | see below |

High resonance and drive can get loud. `limiter` is a lookahead brickwall at -0.45 dBFS with 2 ms lookahead, which adds 2 ms of latency. `soft clip` is a zero-latency tanh stage. If you run the insert slot with a partial dry/wet mix and hear high-frequency comb filtering, switch to `soft clip` or `off`.

## Development

`lib/filter.sc` is a SuperCollider class (`FxFilter : FxBase`), compiled into the sclang class library on norns. After editing it, restart norns or recompile the class library. One `makeDef` builds every model's SynthDef from a shared modulation and drive skeleton; each model plugs in a small filter core. `lib/mod.lua` adds the params and drives the clock sync. Params travel over OSC on `/fx_filter/set`; model changes go to `/fx_filter/model`, which frees and recreates the synth on the current slot.

## Credits

- [sixolet](https://llllllll.co/u/sixolet) for the fx mod framework
- DFM1 by Tony Hardie-Bick, SVF and BMoog from sc3-plugins
