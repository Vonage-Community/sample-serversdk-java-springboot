package com.vonage.sample.serversdk.springboot.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("Vonage Java Server SDK demo")
public class Homepage extends VerticalLayout {

    public Homepage() {
        add(new Anchor("https://github.com/Vonage/vonage-java-sdk", "Vonage Java SDK"));
        add(new Anchor("https://developer.vonage.com/en/documentation", "Documentation"));
        // TODO add divider
        linkToPages("Account", "Messages", "Voice", "Verify");
    }

    private void linkToPages(String... names) {
        for (String name : names) {
            add(new Button(name, event -> getUI().ifPresent(ui ->
                    ui.getPage().executeJs("window.location.href = '"+name.toLowerCase()+"';"))
            ));
        }
    }
}
