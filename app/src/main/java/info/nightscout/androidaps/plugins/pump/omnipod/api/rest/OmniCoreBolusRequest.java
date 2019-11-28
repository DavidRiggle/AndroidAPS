package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import java.math.BigDecimal;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OmniCoreBolusRequest extends OmniCoreRequest {
    public OmniCoreBolusRequest(BigDecimal bolusAmount) {
        super();
        joRequest.addProperty("Type", "Bolus");
        joRequest.addProperty("ImmediateUnits", bolusAmount);
    }

    @Override
    public String getRequestType()  {
        Float bolusAmount = joRequest.get("ImmediateUnits").getAsFloat();
        return String.format(MainApp.gs(R.string.omnipod_command_bolus),bolusAmount);
    }
}
