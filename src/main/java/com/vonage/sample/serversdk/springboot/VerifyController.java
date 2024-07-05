package com.vonage.sample.serversdk.springboot;

import com.vonage.client.verify2.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
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
		verifyParams.toNumber = System.getenv("TO_NUMBER");
		verifyParams.fromNumber = System.getenv("VONAGE_FROM_NUMBER");
		verifyParams.toEmail = System.getenv("TO_EMAIL");
		verifyParams.fromEmail = System.getenv("VONAGE_FROM_EMAIL");
		model.addAttribute("verifyParams", verifyParams);
		return VERIFY_START_TEMPLATE;
	}

	@PostMapping("/postVerificationRequest")
	public String postVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			var builder = VerificationRequest.builder().brand(verifyParams.brand);
			String toNumber = verifyParams.toNumber, toEmail = verifyParams.toEmail,
					fromNumber = verifyParams.fromNumber, fromEmail = verifyParams.fromEmail;

			if (fromNumber != null && fromNumber.isBlank()) {
				fromNumber = null;
			}
			if (fromEmail != null && fromEmail.isBlank()) {
				fromEmail = null;
			}

			var channel = Channel.valueOf(verifyParams.selectedChannel);
			boolean codeless = (channel == Channel.SILENT_AUTH || channel == Channel.WHATSAPP_INTERACTIVE);
			verifyParams.codeless = codeless;

			if (toNumber.matches("0+") || "test@example.com".equals(toEmail)) {
				verifyParams.requestId = UUID.randomUUID();
				if (channel == Channel.SILENT_AUTH) {
					verifyParams.checkUrl = URI.create("https://api.vonage.com/v2/verify/" +
							verifyParams.requestId + "silent-auth/redirect"
					);
				}
			}
			else {
				builder.addWorkflow(switch (channel) {
					case EMAIL -> new EmailWorkflow(toEmail, fromEmail);
					case SMS -> SmsWorkflow.builder(toNumber).from(fromNumber).build();
					case VOICE -> new VoiceWorkflow(toNumber);
					case WHATSAPP -> new WhatsappWorkflow(toNumber, fromNumber);
					case WHATSAPP_INTERACTIVE -> new WhatsappCodelessWorkflow(toNumber, fromNumber);
					case SILENT_AUTH -> new SilentAuthWorkflow(toNumber);
				});

				if (!codeless && verifyParams.codeLength != null) {
					builder.codeLength(verifyParams.codeLength);
				}
				var request = builder.build();
				assert request.isCodeless() == codeless;
				var response = getVerifyClient().sendVerification(request);
				verifyParams.requestId = response.getRequestId();
				verifyParams.checkUrl = response.getCheckUrl();
			}
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

	@lombok.Data
	public static class VerifyParams {
		private boolean codeless;
		private UUID requestId;
		private URI checkUrl;
		private String brand, selectedChannel, userCode,
				toNumber, toEmail, fromNumber, fromEmail;
		private Channel[] channelOptions;
		private Integer codeLength;
	}
}
