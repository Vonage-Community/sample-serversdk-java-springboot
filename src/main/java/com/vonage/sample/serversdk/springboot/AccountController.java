package com.vonage.sample.serversdk.springboot;

import com.vonage.client.account.AccountClient;
import com.vonage.client.insight.CallerType;
import com.vonage.client.insight.CarrierDetails;
import com.vonage.client.insight.InsightClient;
import com.vonage.client.insight.PortedStatus;
import com.vonage.client.numbers.ListNumbersFilter;
import com.vonage.client.numbers.NumbersClient;
import com.vonage.client.numbers.SearchNumbersFilter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.stream.Collectors;

@Controller
public class AccountController extends VonageController {
	static final String
			ACCOUNT_TEMPLATE = "account",
			ACCOUNT_PARAMS_NAME = "accountParams";

	protected AccountClient getAccountClient() {
		return getVonageClient().getAccountClient();
	}

	protected NumbersClient getNumbersClient() {
		return getVonageClient().getNumbersClient();
	}

	protected InsightClient getInsightClient() {
		return getVonageClient().getInsightClient();
	}

	String addParamsAndReturnTemplate(AccountParams accountParams, Model model) {
		var balanceResponse = getAccountClient().getBalance();
		accountParams.setBalance(formatMoney("EUR", balanceResponse.getValue()));
		model.addAttribute(ACCOUNT_PARAMS_NAME, accountParams);
		return ACCOUNT_TEMPLATE;
	}

	@PostMapping("buyNumber")
	public String buyNumber(@ModelAttribute(ACCOUNT_PARAMS_NAME) AccountParams params, Model model) {
		getNumbersClient().buyNumber(params.getCountry(), params.getMsisdn());
		return addParamsAndReturnTemplate(params, model);
	}

	@PostMapping("cancelNumber")
	public String cancelNumber(@ModelAttribute(ACCOUNT_PARAMS_NAME) AccountParams params, Model model) {
		getNumbersClient().cancelNumber(params.getCountry(), params.getMsisdn());
		return addParamsAndReturnTemplate(params, model);
	}

	String formatCarrier(CarrierDetails carrier) {
		String name = carrier.getName(), code = carrier.getNetworkCode(), result = "";
		if (carrier.getNetworkType() != null) {
			result += carrier.getNetworkType().name();
			if (name != null) {
				result += " - ";
			}
		}
		if (name != null) {
			result += name;
		}
		if (code != null) {
			result += " (" + code + ")";
		}
		return result;
	}

	@PostMapping("numberInsight")
	public String numberInsight(@ModelAttribute(ACCOUNT_PARAMS_NAME) AccountParams params, Model model) {
		var insight = getInsightClient().getStandardNumberInsight(params.getMsisdn(), params.getCountry());
		params.setStatus(insight.getStatusMessage());
		var currentCarrier = insight.getCurrentCarrier();
		if (currentCarrier != null) {
			params.setCurrentCarrier("Current carrier: " + formatCarrier(currentCarrier));
		}
		var originalCarrier = insight.getOriginalCarrier();
		if (originalCarrier != null) {
			params.setOriginalCarrier("Original carrier: " + formatCarrier(originalCarrier));
		}
		var ported = insight.getPorted();
		if (ported != null && ported != PortedStatus.UNKNOWN) {
			params.setPorted(ported.name());
		}
		var callerType = insight.getCallerType();
		if (callerType != null && callerType != CallerType.UNKNOWN) {
			params.setCallerType(callerType.name());
		}
		var callerName = insight.getCallerName();
		if (callerName != null && !callerName.isBlank()) {
			params.setCallerName(callerName);
		}
		return addParamsAndReturnTemplate(params, model);
	}

	@ResponseBody
	@GetMapping("/getOwnedNumbers")
	public String numbersInfo() {
		return '[' + Arrays.stream(getNumbersClient().listNumbers().getNumbers())
				.map(n ->
						"{\"msisdn\":\"" + n.getMsisdn() +
						"\",\"country\":\"" + n.getCountry() +
						"\",\"type\":\"" + n.getType() +
						"\",\"features\":[" + (n.getFeatures() == null || n.getFeatures().length == 0 ? "" :
								Arrays.stream(n.getFeatures())
										.map(f -> '"'+f+'"')
										.collect(Collectors.joining(","))
						) + "]}"
				)
				.collect(Collectors.joining(",")) + ']';
	}

	@GetMapping("/account")
	public String accountInfo(Model model) {
		return addParamsAndReturnTemplate(new AccountParams(), model);
	}

	@ResponseBody
	@GetMapping("getPricingAndNumberForCountry")
	public String getPricingAndNumberForCountry(@RequestParam String cc) {
		try {
			final long pauseMillis = 600;
			var sms = getAccountClient().getSmsPrice(cc);
			Thread.sleep(pauseMillis);

			var voice = getAccountClient().getVoicePrice(cc);
			var smsFormatted = formatMoney(sms.getCurrency(), sms.getDefaultPrice().doubleValue());
			var voiceFormatted = formatMoney(voice.getCurrency(), voice.getDefaultPrice().doubleValue());
			Thread.sleep(pauseMillis);

			var numbers = getNumbersClient().searchNumbers(new SearchNumbersFilter(cc)).getNumbers();
			var number = numbers[(int) (Math.random() * numbers.length)];
			var features = Arrays.stream(number.getFeatures())
					.map(f -> '"'+f+'"').collect(Collectors.joining(","));

			return "{\"sms\":\"" + smsFormatted + "\",\"voice\":\"" +
					voiceFormatted + "\",\"msisdn\":\"" + number.getMsisdn() +
					"\",\"type\":\"" + number.getType() + "\",\"cost\":\"" +
					number.getCost() + "\",\"features\":[" + features + "]}";
		}
		catch (Exception ex) {
			String message;
			if (ex instanceof ArrayIndexOutOfBoundsException) {
				message = "No numbers available.";
			}
			else if (ex.getCause() != null) {
				message = ex.getCause().getMessage();
			}
			else {
				message = ex.getMessage();
			}
			return "{\"error\":\"" + message + "\"}";
		}
	}

	public static class AccountParams {
		private String status, balance, country, msisdn, callerName,
				currentCarrier, originalCarrier, ported, callerType;

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getBalance() {
			return balance;
		}

		public void setBalance(String balance) {
			this.balance = balance;
		}

		public String getCountry() {
			return country;
		}

		public void setCountry(String country) {
			this.country = country;
		}

		public String getMsisdn() {
			return msisdn;
		}

		public void setMsisdn(String msisdn) {
			this.msisdn = msisdn;
		}

		public String getCurrentCarrier() {
			return currentCarrier;
		}

		public void setCurrentCarrier(String currentCarrier) {
			this.currentCarrier = currentCarrier;
		}

		public String getOriginalCarrier() {
			return originalCarrier;
		}

		public void setOriginalCarrier(String originalCarrier) {
			this.originalCarrier = originalCarrier;
		}

		public String getPorted() {
			return ported;
		}

		public void setPorted(String ported) {
			this.ported = ported;
		}

		public String getCallerType() {
			return callerType;
		}

		public void setCallerType(String callerType) {
			this.callerType = callerType;
		}

		public String getCallerName() {
			return callerName;
		}

		public void setCallerName(String callerName) {
			this.callerName = callerName;
		}
	}
}
