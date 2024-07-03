package com.vonage.sample.serversdk.springboot;

import com.vonage.client.camara.numberverification.NumberVerificationClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@Controller
public final class NetworkFraudController extends VonageController {

    private NumberVerificationClient getNumberVerificationClient() {
        return getVonageClient().getNumberVerificationClient();
    }

    @ResponseBody
    @GetMapping("/camara/numberVerify")
    public String startVerification(@RequestParam String msisdn, @RequestParam String ngrok) {
        var redirectUrl = URI.create("https://"+ngrok+".ngrok.app/webhooks/camara/numberVerify");
        try {
            return getNumberVerificationClient().initiateVerification(
                    msisdn, redirectUrl, UUID.randomUUID().toString()
            ).toString();
        }
        catch (Exception ex) {
            return ex.getMessage();
        }
    }

    @ResponseBody
    @GetMapping("/webhooks/camara/numberVerify")
    public String inboundWebhook(@RequestParam String code, @RequestParam(required = false) String state) {
        System.out.println("Received code '"+code+"' with state '"+state+"'.");
        boolean result = getNumberVerificationClient().verifyNumber(code);
        System.out.println("Result is "+result);
        return result ? "Number matches." : "Fraudulent!";
    }
}
