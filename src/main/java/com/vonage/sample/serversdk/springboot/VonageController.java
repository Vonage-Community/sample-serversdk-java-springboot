package com.vonage.sample.serversdk.springboot;

import com.vonage.client.VonageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.logging.Logger;

public abstract class VonageController {
	static final String ERROR_TEMPLATE = "error";

	protected Logger logger = Logger.getLogger("controller");

	@Autowired
	private ApplicationConfiguration configuration;

	protected VonageClient getVonageClient() {
		return configuration.getVonageClient();
	}

	protected String errorTemplate(Model model, Exception ex) {
		model.addAttribute("message", ex.getMessage());
		return ERROR_TEMPLATE;
	}

	protected String standardWebhookResponse() {
		return "OK";
	}

	protected String formatInstant(Instant timestamp) {
		var localTime = ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault());
		return DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).format(localTime);
	}

	protected String formatMoney(String currency, double amount) {
		var formatter = NumberFormat.getCurrencyInstance();
		formatter.setCurrency(Currency.getInstance(currency));
		return formatter.format(amount);
	}
}
