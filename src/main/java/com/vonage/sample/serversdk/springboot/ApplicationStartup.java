package com.vonage.sample.serversdk.springboot;

import com.vonage.client.application.Application;
import com.vonage.client.application.ApplicationResponseException;
import com.vonage.client.application.capabilities.Messages;
import com.vonage.client.application.capabilities.Verify;
import com.vonage.client.application.capabilities.Voice;
import com.vonage.client.common.HttpMethod;
import com.vonage.client.common.Webhook;
import static com.vonage.sample.serversdk.springboot.ApplicationConfiguration.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.logging.Logger;

@Component
public class ApplicationStartup {
    private final Logger logger = Logger.getLogger("startup");

    @Autowired
    private ApplicationConfiguration configuration;

    private Webhook buildWebhook(String endpoint) {
        return Webhook.builder()
                .address(resolveEndpoint(endpoint).toString())
                .method(HttpMethod.POST).build();
    }

    private URI resolveEndpoint(String endpoint) {
        return configuration.serverUrl.resolve(endpoint);
    }

    @PostConstruct
    public void init() {
        var ac = configuration.vonageClient.getApplicationClient();
        var appIdStr = configuration.applicationId.toString();
        try {
            var existing = ac.getApplication(appIdStr);
            var application = ac.updateApplication(
                    Application.builder(existing)
                            .improveAi(true)
                            .addCapability(Verify.builder()
                                    .addWebhook(Webhook.Type.STATUS, buildWebhook(VERIFY_STATUS_ENDPOINT)).build()
                            ).addCapability(Messages.builder()
                                    .addWebhook(Webhook.Type.INBOUND, buildWebhook(INBOUND_MESSAGE_ENDPOINT))
                                    .addWebhook(Webhook.Type.STATUS, buildWebhook(MESSAGE_STATUS_ENDPOINT))
                                    .build()
                            )
                            .addCapability(Voice.builder()
                                    .addWebhook(Webhook.Type.ANSWER, buildWebhook(VOICE_ANSWER_ENDPOINT))
                                    .addWebhook(Webhook.Type.EVENT, buildWebhook(VOICE_EVENT_ENDPOINT))
                                    .build()
                            ).build()
            );
            assert application != null;
        }
        catch (ApplicationResponseException ex) {
            logger.warning("Failed to update application "+appIdStr+": "+ex.getMessage());
            throw ex;
        }
    }
}
