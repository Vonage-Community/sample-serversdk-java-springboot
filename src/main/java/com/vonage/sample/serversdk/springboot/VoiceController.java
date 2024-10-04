package com.vonage.sample.serversdk.springboot;

import com.vonage.client.voice.*;
import com.vonage.client.voice.ncco.TalkAction;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class VoiceController extends VonageController {
	static final String
			VOICE_TEMPLATE = "voice",
			VOICE_CALL_PARAMS_NAME = "voiceCallParams";

	private final Map<String, EventWebhook> callEvents = new HashMap<>();

	protected VoiceClient getVoiceClient() {
		return getVonageClient().getVoiceClient();
	}

	@ResponseBody
	@GetMapping("getSpokenLanguages")
	public String getSpokenLanguages() throws Exception {
		return "["+ Arrays.stream(TextToSpeechLanguage.values())
				.map(lang -> '"'+lang.name()+'"')
				.collect(Collectors.joining(",")) + "]";
	}

	@GetMapping("/voice")
	public String voiceCall(Model model) {
		var params = new VoiceCallParams();
		params.language = TextToSpeechLanguage.UNITED_KINGDOM_ENGLISH;
		params.tts = "Hello, World!";
		params.toPstn = System.getenv("TO_NUMBER");
		params.premium = true;
		params.ringTimer = 20;
		model.addAttribute(VOICE_CALL_PARAMS_NAME, params);
		return VOICE_TEMPLATE;
	}

	@PostMapping("textToSpeechRequest")
	public String textToSpeechRequest(@ModelAttribute(VOICE_CALL_PARAMS_NAME) VoiceCallParams params, Model model) {
		try {
			var event = getVoiceClient().createCall(Call.builder()
					.machineDetection(MachineDetection.CONTINUE)
					.to(new PhoneEndpoint(params.toPstn))
					.ncco(TalkAction.builder(params.tts)
							.language(params.language)
							.premium(params.premium).build()
					).lengthTimer(25)
					.ringingTimer(params.ringTimer)
					.fromRandomNumber(true).build()
			);
			params.callId = event.getUuid();
			model.addAttribute(VOICE_CALL_PARAMS_NAME, params);
			return VOICE_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@ResponseBody
	@GetMapping("getVoiceCallStatusUpdate")
	public String getVoiceCallStatusUpdate(@RequestParam String callId, @RequestParam long timeout) {
		EventWebhook event;
		synchronized (callEvents) {
			if ((event = callEvents.remove(callId)) == null) {
				try {
					callEvents.wait(timeout);
				}
				catch (InterruptedException ie) {
					// Continue
				}
				event = callEvents.remove(callId);
			}
		}
		if (event == null) return "";
		return "{\"status\":\""+event.getStatus()+"\",\"detail\":\""+event.getDetail()+"\"}";
	}

	@ResponseBody
	@PostMapping("/webhooks/voice/answer")
	public String answerWebhook(@RequestBody String payload) {
		var parsed = AnswerWebhook.fromJson(payload);
		logger.info("Call "+parsed.getUuid()+" answered");
		return standardWebhookResponse();
	}

	@ResponseBody
	@PostMapping("/webhooks/voice/event")
	public String eventWebhook(@RequestBody String payload) {
		var parsed = EventWebhook.fromJson(payload);
		synchronized (callEvents) {
			callEvents.put(parsed.getCallUuid(), parsed);
			callEvents.notify();
		}
		return standardWebhookResponse();
	}

	@lombok.Data
	public static class VoiceCallParams {
		private TextToSpeechLanguage language;
		private String toPstn, tts, callId;
		private boolean premium;
		private Integer ringTimer;
	}
}
