//verison 0.0.1

// Inherit from CroneEngine
Engine_CartersDelay : CroneEngine {
	var oscs, kernel, b, timer, micBus, ptrBus, panBus, cutBus, resBus,
	micGrp, ptrGrp, recGrp, granGrp, panLFOs, cutoffLFOs, resonanceLFOs,
	rates, durs, delays, a, g, h, i;
	var fb;

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}
	alloc {
		var s = context.server;
		"alloc".postln;
		oscs = Dictionary.new();
		timer = LinkClock.new(2).latency_(s.latency).quantum_(0);
		b = Buffer.alloc(s, s.sampleRate * (timer.beatDur*512), 1);
		micBus = Bus.audio(s, 1);
		ptrBus = Bus.audio(s, 1);
		SynthDef(\mic, {
			arg in = 0, out = 0, amp = 1;
			var sig;
			sig = SoundIn.ar(in) * amp;
			Out.ar(out, sig);
		}).add;
		SynthDef(\ptr, {
			arg out = 0, buf = 0, rate = 1;
			var sig;
			sig = Phasor.ar(0, BufRateScale.kr(buf)*rate, 0, BufFrames.kr(buf));
			Out.ar(out, sig);
		}).add;
		SynthDef(\rec, {
			arg ptrIn = 0, micIn = 0, buf = 0, preLevel = 0;
			var ptr, sig;
			ptr = In.ar(ptrIn, 1);
			sig = In.ar(micIn, 1);
			sig = sig + (BufRd.ar(1, buf, ptr) * preLevel);
			BufWr.ar(sig, buf, ptr);
		}).add;
		// downmixing feedbacking saturating filtering patchcord
		SynthDef(\fbPatchMix, { 
			arg in=0, out=0, amp=0, balance=0, hpFreq=12, 
			// eh, why not
				noiseLevel=0.0, sineLevel=0, sineHz=55;
			var input = InFeedback.ar(in, 2);
			var output;
			output = Balance.ar(input[0], input[1], balance);
			output = output + (PinkNoise.ar * noiseLevel);
			output = output + (SinOsc.ar(sineHz) * sineLevel);
			output = HPF.ar(output, hpFreq);
			output = output.softclip;
			Out.ar(out, output * amp);
		}).add;
		SynthDef(\gran, {
			arg amp = 0.5, buf = 0, out = 0,
			atk = 1, rel = 1, gate = 1,
			sync = 1, dens = 40,
			baseDur = 0.05, durRand = 1,
			rate = 1, rateRand = 1,
			pan = 0, panRand = 0,
			grainEnv = (-1), ptrBus = 0, ptrSampleDelay = 20000,
			ptrRandSamples = 5000, minPtrDelay = 1000;
			var sig, env, densCtrl, durCtrl, rateCtrl, panCtrl,
			ptr, ptrRand, totalDelay, maxGrainDur;
			env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction: 2);
			densCtrl = Select.ar(sync, [Dust.ar(dens), Impulse.ar(dens)]);
			durCtrl = baseDur * LFNoise1.ar(100).exprange(1/durRand, durRand);
			rateCtrl = rate.lag3(0.5) * LFNoise1.ar(100).exprange(1/rateRand, rateRand);
			panCtrl = pan + LFNoise1.kr(100).bipolar(panRand);
			ptrRand = LFNoise1.ar(100).bipolar(ptrRandSamples);
			totalDelay = max(ptrSampleDelay - ptrRand, minPtrDelay);
			ptr = In.ar(ptrBus, 1);
			ptr = ptr - totalDelay;
			ptr = ptr / BufFrames.kr(buf);
			maxGrainDur = (totalDelay / rateCtrl) / SampleRate.ir;
			durCtrl = min(durCtrl, maxGrainDur);
			sig = GrainBuf.ar(
				2,
				densCtrl,
				durCtrl,
				buf,
				rateCtrl,
				ptr,
				4,
				panCtrl,
				grainEnv
			);
			sig = MoogFF.ar(
				sig * env * amp,
				freq: \cutoff.kr(12000),
				gain: \resonance.kr(1)
			);
			Out.ar(out, sig);
			Group.tail
		}).add;
		s.sync;
		micGrp = Group.new;
		ptrGrp = Group.after(micGrp);
		recGrp = Group.after(ptrGrp);
		granGrp = Group.after(recGrp);
		a = Synth(\mic, [\in, 0, \out, micBus, \amp, 0.5], micGrp);
		h = Synth(\ptr, [\buf, b, \out, ptrBus], ptrGrp);
		i = Synth(\rec, [\ptrIn, ptrBus, \micIn, micBus, \buf, b], recGrp);
		fb = Synth(\fbPatchMix, [\in, 0, \out, micBus], micGrp, addAction:\addToHead);
		panLFOs = Array.fill(16, {0});
		cutoffLFOs = Array.fill(16, {0});
		resonanceLFOs = Array.fill(16, {0});
		16.do({
			arg i;
			panLFOs.put(i,
				Ndef(i.asSymbol, {
					LFTri.kr(timer.beatDur/rrand(1,64)).range(-1,1);
				})
			);
			cutoffLFOs.put(i,
				Ndef((i+16).asSymbol, {
					LFTri.kr(timer.beatDur/rrand(1,64)).range(500,15000);
				})
			);
			resonanceLFOs.put(i,
				Ndef((i+32).asSymbol, {
					LFTri.kr(timer.beatDur/rrand(1,64)).range(0,2);
				})
			)
		});
		rates = [1/4,1/2,1,3/2,2].scramble;
		durs = 16.collect({arg i; timer.beatDur*(i+1)}).scramble;
		delays = 16.collect({arg i; s.sampleRate*(timer.beatDur*(i+1))*16}).scramble;
		g = 16.collect({
			arg n;
			Synth(\gran, [
				\amp, 0,
				\buf, b,
				\out, 0,
				\atk, 1,
				\rel, 1,
				\gate, 1,
				\sync, 1,
				\dens, 1/(durs[n]*rates[n%5]),
				\baseDur, durs[n],
				\durRand, 1,
				\rate, rates[n%5],
				\rateRand, 1,
				\pan, panLFOs[n],
				\panRand, 0,
				\grainEnv, -1,
				\ptrBus, ptrBus,
				\ptrSampleDelay, delays[n],
				\ptrRandSamples, s.sampleRate*(timer.beatDur*((n%8)+1))*2,
				\minPtrDelay, delays[n],
				\cutoff, cutoffLFOs[n],
				\resonance, resonanceLFOs[n]
			], granGrp;
			)
		});
		oscs.put("receiver",
			OSCFunc.new({ |msg, time, addr, recvPort|
				var voice;
				if(
					msg[1] == 2,
					{
						a.set(\amp,msg[2])
					}
				);
				if(
					msg[1] == 3,
					{
						if(
							msg[2] == 1,
							{
								16.do(
									{
										arg i;
										g[i].set(\amp,0)
									}
								)
							},
							{
								16.do(
									{
										arg i;
										g[i].set(\amp,msg[2])
									}
								)
							}
						)
					}
				);
				if (msg[1] == 4,
					// set preserve level
					{
						i.set(\preLevel, msg[2]);
					}
				);
				if (msg[1] == 5,
					// set feedback level
					{
						fb.set(\amp, msg[2]);
					}
				);
				if (msg[1] == 6,
					// set feedback balance
					{
						fb.set(\balance, msg[2]);
					}
				);
				if (msg[1] == 7,
					// set feedback highpass frequency
					{
						fb.set(\hpFreq, msg[2]);
					}
				);
				if (msg[1] == 8,
					// set feedback noise level
					{
						fb.set(\noiseLevel, msg[2]);
					}
				);
				if (msg[1] == 9,
					// set feedback sine level
					{
						fb.set(\sineLevel, msg[2]);
					}
				);
				if (msg[1] == 10,
					// set feedback sine frequency
					{
						fb.set(\sineHz, msg[2]);
					}
				);
			}, "/receiver");
		);

	}
	free {
		b.free;
		timer.free;
		micBus.free;
		ptrBus.free;
		micGrp.free;
		ptrGrp.free;
		recGrp.free;
		granGrp.free;
		16.do(
			{
				arg i;
				cutoffLFOs[i].free;
				resonanceLFOs[i].free;
				panLFOs[i].free;
			}
		);
	}
}