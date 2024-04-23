package com.vonage.sample.serversdk.springboot;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ApplicationController extends VonageController {

	@ResponseBody
	@GetMapping("/_/health")
	public String health() {
		return standardWebhookResponse();
	}
}
