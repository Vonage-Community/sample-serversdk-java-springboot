package com.vonage.sample.serversdk.springboot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vonage.client.verify2.*;
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
		verifyParams.setChannelOptions(Channel.values());
		verifyParams.setBrand("Vonage");
		verifyParams.setTo(System.getenv("TO_NUMBER"));
		model.addAttribute("verifyParams", verifyParams);
		return VERIFY_START_TEMPLATE;
	}

	@PostMapping("/postVerificationRequest")
	public String postVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			var builder = VerificationRequest.builder().brand(verifyParams.getBrand());
			String to = verifyParams.getTo(), from = verifyParams.getFrom();
			var channel = Channel.valueOf(verifyParams.getSelectedChannel());
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
			verifyParams.setCodeless(request.isCodeless());
			verifyParams.setRequestId(response.getRequestId());
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
			if (verifyParams.isCodeless()) {
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

	public static class VerifyParams {
		private boolean codeless;
		private UUID requestId;
		private String brand, selectedChannel, to, from, userCode;
		private Channel[] channelOptions;
		private Integer codeLength;

		public boolean isCodeless() {
			return codeless;
		}

		public void setCodeless(boolean codeless) {
			this.codeless = codeless;
		}

		public UUID getRequestId() {
			return requestId;
		}

		public void setRequestId(UUID requestId) {
			this.requestId = requestId;
		}

		public String getBrand() {
			return brand;
		}

		public void setBrand(String brand) {
			this.brand = brand;
		}

		public String getSelectedChannel() {
			return selectedChannel;
		}

		public void setSelectedChannel(String selectedChannel) {
			this.selectedChannel = selectedChannel;
		}

		public String getTo() {
			return to;
		}

		public void setTo(String to) {
			this.to = to;
		}

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getUserCode() {
			return userCode;
		}

		public void setUserCode(String userCode) {
			this.userCode = userCode;
		}

		public Channel[] getChannelOptions() {
			return channelOptions;
		}

		public void setChannelOptions(Channel[] channelOptions) {
			this.channelOptions = channelOptions;
		}

		public Integer getCodeLength() {
			return codeLength;
		}

		public void setCodeLength(Integer codeLength) {
			this.codeLength = codeLength;
		}
	}
}
