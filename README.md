# fx_filter for norns

A stereo [DFM1](https://doc.sccode.org/Classes/DFM1.html) filter as a norns effect, built on sixolet's [fx mod](https://llllllll.co/t/fx-mod/62726) framework. Runs on send A, send B, or as an insert with dry/wet, on top of any script.

> DFM1 is a digitally modelled analog filter. It provides low-pass and high-pass filtering. The filter can be overdriven and will self-oscillate at high resonances.

Features:

- lowpass / highpass, resonance into self-oscillation
- tanh drive before or after the filter
- stereo cutoff spread
- input envelope follower to cutoff
- LFO to cutoff, free-running or synced to the norns clock
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
| cutoff | 20..20000 Hz | 1000 | base cutoff before modulation |
| resonance | 0..1.2 | 0.1 | above ~1.0 self-oscillates |
| type | lowpass, highpass | lowpass | |
| input gain | 0.1..8 | 1 | DFM1 input gain, pushes the filter core |
| stereo spread | 0..1 | 0 | offsets L/R cutoff, up to ±35% |
| noise | 0..0.005 | 0.0003 | DFM1 analog noise floor; tiny values keep it lively |

### drive

| param | range | default | notes |
| --- | --- | --- | --- |
| drive mode | off, pre-filter, post-filter | off | post tames self-oscillation peaks |
| drive amount | 1..8 | 1 | tanh with level compensation |

### envelope

Envelope follower on the input signal, adds to cutoff in octaves.

| param | range | default | notes |
| --- | --- | --- | --- |
| env > cutoff | -4..4 oct | 0 | negative values duck the cutoff |
| env sensitivity | 0..20 | 2 | input gain into the follower |
| env attack | 1 ms..1 s | 10 ms | |
| env release | 10 ms..2 s | 200 ms | |

### lfo

Bipolar LFO on cutoff.

| param | range | default | notes |
| --- | --- | --- | --- |
| lfo shape | sine, triangle, saw, square, s&h, noise | sine | |
| lfo mode | free, sync | free | |
| lfo rate | 0.01..20 Hz | 1 Hz | shown in free mode |
| lfo division | 4 bars .. 1/32, dotted and triplet | 1/4 | shown in sync mode |
| lfo > cutoff | -127..127 | 0 | ±127 = ±4 octaves |

In **sync** mode the rate follows the norns clock tempo and the LFO phase restarts on every division boundary, so it stays locked through tempo changes and transport jumps. Divisions assume 4/4. Saw ramps from bottom to top across each cycle, square is high for the first half, s&h steps on each cycle, noise is smooth and not phase-locked.

In **free** mode the LFO runs at the set rate and ignores the clock.

### output

| param | range | default | notes |
| --- | --- | --- | --- |
| level | -24..12 dB | 0 dB | |
| limiter | off, soft clip, limiter | limiter | see below |

High resonance and drive can get loud. `limiter` is a lookahead brickwall at -0.45 dBFS with 2 ms lookahead, which adds 2 ms of latency. `soft clip` is a zero-latency tanh stage. If you run the insert slot with a partial dry/wet mix and hear high-frequency comb filtering, switch to `soft clip` or `off`.

## Development

`lib/filter.sc` is a SuperCollider class (`FxFilter : FxBase`), compiled into the sclang class library on norns. After editing it, restart norns or recompile the class library. `lib/mod.lua` adds the params and drives the clock sync. Both talk over OSC on the `/fx_filter/set` path.

## Credits

- [sixolet](https://llllllll.co/u/sixolet) for the fx mod framework
- DFM1 by Tony Hardie-Bick, from sc3-plugins
