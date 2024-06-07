package com.vonage.sample.serversdk.springboot;

import com.vonage.client.messages.*;
import static com.vonage.client.messages.MessageType.*;
import com.vonage.client.messages.messenger.*;
import com.vonage.client.messages.mms.*;
import com.vonage.client.messages.sms.*;
import com.vonage.client.messages.viber.*;
import com.vonage.client.messages.whatsapp.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public final class MessagesController extends VonageController {
	static final String
			MESSAGES_TEMPLATE = "messages",
			MESSAGE_PARAMS_NAME = "messageParams";

	private final Map<UUID, InboundMessage> inboundMessages = new HashMap<>();
	private final Map<UUID, MessageStatus> messageStatuses = new HashMap<>();

	private MessageRequest applyCommonParams(MessageRequest.Builder<?, ?> builder, MessageParams params) {
		return builder.from(params.from).to(params.to).build();
	}

	private static String nullifyIfEmpty(String param) {
		return param == null || param.isBlank() ? null : param;
	}

	MessageRequest buildMessage(MessageParams params) {
		String url = nullifyIfEmpty(params.url), text = nullifyIfEmpty(params.text);
		var channel = Channel.valueOf(params.selectedChannel);
		var messageType = MessageType.valueOf(params.selectedType);

		MessageRequest.Builder<?, ?> builder = switch (channel) {
			case SMS -> SmsTextRequest.builder().text(text);
			case WHATSAPP -> switch (messageType) {
				case TEXT -> WhatsappTextRequest.builder().text(text);
				case AUDIO -> WhatsappAudioRequest.builder().url(url);
				case IMAGE -> WhatsappImageRequest.builder().url(url).caption(text);
				case VIDEO -> WhatsappVideoRequest.builder().url(url).caption(text);
				case FILE -> WhatsappFileRequest.builder().url(url).caption(text);
				case STICKER -> WhatsappStickerRequest.builder().url(url).id(text);
				case LOCATION -> WhatsappLocationRequest.builder()
                        .name(params.text).address(params.address)
                        .latitude(params.latitude).longitude(params.longitude);
				default -> throw new IllegalStateException();
			};
			case MMS -> switch (messageType) {
				case VCARD -> MmsVcardRequest.builder().url(url).caption(text);
				case AUDIO -> MmsAudioRequest.builder().url(url).caption(text);
				case IMAGE -> MmsImageRequest.builder().url(url).caption(text);
				case VIDEO -> MmsVideoRequest.builder().url(url).caption(text);
				default -> throw new IllegalStateException();
			};
			case MESSENGER -> switch (messageType) {
				case TEXT -> MessengerTextRequest.builder().text(text);
				case IMAGE -> MessengerImageRequest.builder().url(url);
				case AUDIO -> MessengerAudioRequest.builder().url(url);
				case VIDEO -> MessengerVideoRequest.builder().url(url);
				case FILE -> MessengerFileRequest.builder().url(url);
				default -> throw new IllegalStateException();
			};
			case VIBER -> switch (messageType) {
				case TEXT -> ViberTextRequest.builder().text(text);
				case IMAGE -> ViberImageRequest.builder().url(url);
				case FILE -> ViberFileRequest.builder().url(url);
				default -> throw new IllegalStateException();
			};
		};
		return applyCommonParams(builder, params);
	}

	@ResponseBody
	@GetMapping("getSandboxNumbers")
	public String getSandboxNumbers() {
		return "{\"" +
				Channel.WHATSAPP.name()+"\":\""+System.getenv("VONAGE_WHATSAPP_NUMBER") +
				"\",\""+Channel.VIBER.name()+"\":\""+System.getenv("VONAGE_VIBER_ID") +
				"\",\""+Channel.MESSENGER.name()+"\":\""+System.getenv("VONAGE_MESSENGER_ID") +
				"\"}";
	}

	@ResponseBody
	@GetMapping("getMessageTypes")
	public String getMessageTypes(@RequestParam String channel) {
		var channelEnum = Channel.valueOf(channel);
		return "[" + channelEnum.getSupportedOutboundMessageTypes().stream()
				.filter(mt -> mt != TEMPLATE && mt != CUSTOM &&
						(channelEnum != Channel.VIBER || mt != VIDEO)
				)
				.map(mt -> '"'+mt.name()+'"')
				.collect(Collectors.joining(",")) + "]";
	}

	private String setAndReturnTemplate(Model model, MessageParams messageParams) {
		model.addAttribute(MESSAGE_PARAMS_NAME, messageParams);
		return MESSAGES_TEMPLATE;
	}

	@GetMapping("/messages")
	public String messageStart(Model model) {
		var messageParams = new MessageParams();
		messageParams.to = System.getenv("TO_NUMBER");
		messageParams.text = "Hello, World!";
		return setAndReturnTemplate(model, messageParams);
	}

	@PostMapping("/sendMessage")
	public String sendMessage(@ModelAttribute(MESSAGE_PARAMS_NAME) MessageParams messageParams, Model model) {
		try {
			var messageRequest = buildMessage(messageParams);
			var client = getVonageClient().getMessagesClient();
			if (messageParams.sandbox) {
				client.useSandboxEndpoint();
			}
			else {
				client.useRegularEndpoint();
			}
			var response = client.sendMessage(messageRequest);
			messageParams.messageId = response.getMessageUuid();
			return setAndReturnTemplate(model, messageParams);
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@ResponseBody
	@PostMapping("/webhooks/messages/inbound")
	public String inboundWebhook(@RequestBody String payload) {
		var parsed = InboundMessage.fromJson(payload);
		synchronized (inboundMessages) {
			inboundMessages.put(parsed.getMessageUuid(), parsed);
			inboundMessages.notify();
		}
		return standardWebhookResponse();
	}

	@ResponseBody
	@PostMapping("/webhooks/messages/status")
	public String statusWebhook(@RequestBody String payload) {
		var parsed = MessageStatus.fromJson(payload);
		synchronized (messageStatuses) {
			messageStatuses.put(parsed.getMessageUuid(), parsed);
			messageStatuses.notify();
		}
		return standardWebhookResponse();
	}

	@ResponseBody
	@GetMapping("getMessageStatusUpdate")
	public String getMessageStatusUpdate(@RequestParam UUID messageId, @RequestParam long timeout) {
		MessageStatus status;
		synchronized (messageStatuses) {
			if ((status = messageStatuses.remove(messageId)) == null) {
				try {
					messageStatuses.wait(timeout);
				}
				catch (InterruptedException ie) {
					// Continue;
				}
				status = messageStatuses.remove(messageId);
			}
		}
		if (status == null) return "";
		var formatted = status.getStatus().name();
		if (status.getTimestamp() != null) {
			formatted += " at " + formatInstant(status.getTimestamp());
		}
		var usage = status.getUsage();
		if (usage != null) {
			formatted += ", costing "+usage.getCurrency().getSymbol() + usage.getPrice();
		}
		return "{\"text\":\""+formatted+"\"}";
	}

	@ResponseBody
	@GetMapping("getInboundMessage")
	public String getInboundMessage(@RequestParam UUID messageId, @RequestParam long timeout) {
		InboundMessage inbound;
		synchronized (inboundMessages) {
			if ((inbound = inboundMessages.remove(messageId)) == null) {
				try {
					inboundMessages.wait(timeout);
				}
				catch (InterruptedException ie) {
					// Continue;
				}
				inbound = inboundMessages.remove(messageId);
			}
		}
		if (inbound == null) return "";
		var formatted = inbound.getMessageType().name()+" received";
		if (inbound.getTimestamp() != null) {
			formatted += " at " + formatInstant(inbound.getTimestamp());
		}
		var usage = inbound.getUsage();
		if (usage != null) {
			formatted += ", costing "+usage.getCurrency().getSymbol() + usage.getPrice();
		}
		return "{\"text\":\""+formatted+"\"}";
	}

	@lombok.Data
	public static class MessageParams {
		private UUID messageId;
		private boolean sandbox;
		private double latitude, longitude;
		private String from, to, text, url, address, selectedChannel, selectedType;
	}
}
