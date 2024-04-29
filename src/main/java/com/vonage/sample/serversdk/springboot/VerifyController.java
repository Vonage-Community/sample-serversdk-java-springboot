package com.vonage.sample.serversdk.springboot;

import com.vonage.client.verify2.*;
import lombok.Data;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class VerifyController extends VonageController {
	static final String
			VERIFY_START_TEMPLATE = "verify_start",
			VERIFY_RESULT_TEMPLATE = "verify_result";

	private final Map<UUID, VerificationCallback> callbacks = new HashMap<>();

	protected Verify2Client getVerifyClient() {
		return getVonageClient().getVerify2Client();
	}

	@GetMapping("/verify")
	public String verificationRequestForm(Model model) {
		var verifyParams = new VerifyParams();
		verifyParams.channelOptions = Channel.values();
		verifyParams.brand = "Vonage";
		verifyParams.to = System.getenv("TO_NUMBER");
		model.addAttribute("verifyParams", verifyParams);
		return VERIFY_START_TEMPLATE;
	}

	@PostMapping("/postVerificationRequest")
	public String postVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			var builder = VerificationRequest.builder().brand(verifyParams.brand);
			String to = verifyParams.to, from = verifyParams.from;
			if (from != null && from.isBlank()) from = null;
			var channel = Channel.valueOf(verifyParams.selectedChannel);
			builder.addWorkflow(switch (channel) {
				case EMAIL -> new EmailWorkflow(to);
				case SMS -> SmsWorkflow.builder(to).from(from).build();
				case VOICE -> new VoiceWorkflow(to);
				case WHATSAPP -> new WhatsappWorkflow(to, from);
				case WHATSAPP_INTERACTIVE -> new WhatsappCodelessWorkflow(to, from);
				case SILENT_AUTH -> new SilentAuthWorkflow(to);
			});
			if (verifyParams.codeLength != null) {
				builder.codeLength(verifyParams.codeLength);
			}
			var request = builder.build();
			var response = getVerifyClient().sendVerification(request);
			verifyParams.codeless = request.isCodeless();
			verifyParams.requestId = response.getRequestId();
			model.addAttribute("verifyParams", verifyParams);
			return VERIFY_RESULT_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@PostMapping("/checkVerificationRequest")
	public String checkVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			String result;
			if (verifyParams.codeless) {
				VerificationCallback callback;
				if ((callback = callbacks.remove(verifyParams.requestId)) == null) synchronized (callbacks) {
					try {
						callbacks.wait(2000);
						callback = callbacks.remove(verifyParams.requestId);
					}
					catch (InterruptedException ie) {
						// Continue
					}
				}
				if (callback != null) {
					assert verifyParams.requestId.equals(callback.getRequestId());
					result = callback.getStatus().name();
					if (callback.getFinalizedAt() != null) {
						result += " at " + formatInstant(callback.getFinalizedAt());
					}
				}
				else {
					result = "No response received.";
				}
			}
			else {
				try {
					getVerifyClient().checkVerificationCode(verifyParams.requestId, verifyParams.userCode);
					result = "Code matched. Verification successful.";
				}
				catch (VerifyResponseException ex) {
					result = ex.getMessage();
				}
			}
			model.addAttribute("result", result);
			return VERIFY_RESULT_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@PostMapping("/cancelVerificationRequest")
	public String cancelVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			String result;
			try {
				getVerifyClient().cancelVerification(verifyParams.requestId);
				result = "Verification workflow aborted.";
			}
			catch (VerifyResponseException ex) {
				result = ex.getMessage();
			}
			model.addAttribute("result", result);
			return VERIFY_RESULT_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@ResponseBody
	@PostMapping("/webhooks/verify2")
	public String eventsWebhook(@RequestBody String payload) {
		var parsed = VerificationCallback.fromJson(payload);
		synchronized (callbacks) {
			callbacks.put(parsed.getRequestId(), parsed);
			callbacks.notify();
		}
		return standardWebhookResponse();
	}

	@Data
	public static class VerifyParams {
		private boolean codeless;
		private UUID requestId;
		private String brand, selectedChannel, to, from, userCode;
		private Channel[] channelOptions;
		private Integer codeLength;
	}
}
