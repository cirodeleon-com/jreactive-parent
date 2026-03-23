package com.ciro.jreactive.web.components;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.annotations.Prop;
import com.ciro.jreactive.annotations.WebComponent;

@WebComponent(
    tag = "sl-input",
    props = {"name","value", "type", "label", "placeholder"},
    events = {"sl-input"}
)
public class SlInput extends HtmlComponent {
	@Prop public String name = "";
    @Prop public String value = "";
    @Prop public String type = "text";
    @Prop public String label = "";
    @Prop public String placeholder = "";
    @Prop public String onSlInput = "";
}