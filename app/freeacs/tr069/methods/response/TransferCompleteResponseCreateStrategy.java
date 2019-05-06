package freeacs.tr069.methods.response;

import freeacs.tr069.http.HTTPRequestResponseData;
import freeacs.tr069.xml.Body;
import freeacs.tr069.xml.Header;
import freeacs.tr069.xml.Response;
import freeacs.tr069.xml.TR069TransactionID;

public class TransferCompleteResponseCreateStrategy implements ResponseCreateStrategy {
    @SuppressWarnings("Duplicates")
    @Override
    public Response getResponse(HTTPRequestResponseData reqRes) {
        TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
        Header header = new Header(tr069ID, null, null);
        Body body = new Body() {
            @Override
            public String toXmlImpl() {
                return "\t\t<cwmp:TransferCompleteResponse />\n";
            }
        };
        return new Response(header, body, reqRes.getSessionData().getCwmpVersionNumber());
    }
}