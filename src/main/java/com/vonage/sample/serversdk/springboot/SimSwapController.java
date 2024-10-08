package com.vonage.sample.serversdk.springboot;

import com.vonage.client.camara.simswap.SimSwapClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@Controller
public final class SimSwapController extends VonageController {
    private static final String
            SIM_SWAP_TEMPLATE_NAME = "sim_swap",
            SIM_SWAP_PARAMS_NAME = "simSwapParams",
            SIM_SWAP_URL = "/simSwap";

    private SimSwapClient getSimSwapClient() {
        return getVonageClient().getSimSwapClient();
    }

    @GetMapping(SIM_SWAP_URL)
    public String simSwapStart(Model model) {
        var simSwapParams = new SimSwapParams();
        simSwapParams.msisdn = "+990123456";
        model.addAttribute(SIM_SWAP_PARAMS_NAME, simSwapParams);
        return SIM_SWAP_TEMPLATE_NAME;
    }

    @PostMapping(SIM_SWAP_URL)
    public String simSwapPost(@ModelAttribute SimSwapParams simSwapParams, Model model) {
        if (simSwapParams.msisdn != null) {
            simSwapParams.date = getSimSwapClient().retrieveSimSwapDate(simSwapParams.msisdn);
        }
        model.addAttribute(SIM_SWAP_PARAMS_NAME, simSwapParams);
        return SIM_SWAP_TEMPLATE_NAME;
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
