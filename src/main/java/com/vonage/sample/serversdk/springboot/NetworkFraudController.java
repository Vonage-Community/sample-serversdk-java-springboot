package com.vonage.sample.serversdk.springboot;

import com.vonage.client.camara.numberverification.NumberVerificationClient;
import com.vonage.client.camara.simswap.SimSwapClient;
import static com.vonage.sample.serversdk.springboot.ApplicationConfiguration.NUMBER_VERIFICATION_REDIRECT_ENDPOINT;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Controller
public final class NetworkFraudController extends VonageController {
    static final String SIM_SWAP_TEMPLATE_NAME = "sim_swap";

    private NumberVerificationClient getNumberVerificationClient() {
        return getVonageClient().getNumberVerificationClient();
    }

    private SimSwapClient getSimSwapClient() {
        return getVonageClient().getSimSwapClient();
    }

    @GetMapping("/simSwap")
    public String simSwapStart(Model model) {
        var simSwapParams = new SimSwapParams();
        simSwapParams.msisdn = "+990123456";
        model.addAttribute("simSwapParams", simSwapParams);
        return SIM_SWAP_TEMPLATE_NAME;
    }

    @PostMapping("/camara/simSwap")
    public String simSwap(@ModelAttribute SimSwapParams simSwapParams, Model model) {
        if (simSwapParams.msisdn != null) {
            simSwapParams.date = getSimSwapClient().retrieveSimSwapDate(simSwapParams.msisdn);
        }
        model.addAttribute("simSwapParams", simSwapParams);
        return SIM_SWAP_TEMPLATE_NAME;
    }

    @ResponseBody
    @GetMapping("/camara/numberVerify")
    public String startVerification(@RequestParam String msisdn) {
        var redirectUrl = getServerUrl().resolve(NUMBER_VERIFICATION_REDIRECT_ENDPOINT);
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
    @GetMapping(NUMBER_VERIFICATION_REDIRECT_ENDPOINT)
    public String inboundWebhook(@RequestParam String code, @RequestParam(required = false) String state) {
        System.out.println("Received code '"+code+"' with state '"+state+"'.");
        boolean result = getNumberVerificationClient().verifyNumber(code);
        System.out.println("Result is "+result);
        return result ? "Number matches." : "Fraudulent!";
    }

    public static class SimSwapParams {
        private String msisdn;
        private Instant date;

        public String getMsisdn() {
            return msisdn;
        }

        public void setMsisdn(String msisdn) {
            this.msisdn = msisdn;
        }

        public Instant getDate() {
            return date;
        }

        public void setDate(Instant date) {
            this.date = date;
        }
    }
}
