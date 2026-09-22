import cl.chiletv.app.Channel;
import cl.chiletv.app.M3UParser;
import java.util.List;

public class TestParser {
    public static void main(String[] args) {
        String sample = "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"Canal13.cl\" tvg-name=\"Canal 13\" tvg-logo=\"https://example.com/logo.png\" group-title=\"General\",Canal 13\n" +
                "#EXTVLCOPT:http-referrer=https://example.com/\n" +
                "https://example.com/live.m3u8|User-Agent=Test%20Agent\n";
        List<Channel> channels = M3UParser.parse(sample);
        if (channels.size() != 1) throw new AssertionError("Expected 1 channel");
        Channel c = channels.get(0);
        if (!"Canal 13".equals(c.getName())) throw new AssertionError(c.getName());
        if (!"General".equals(c.getGroup())) throw new AssertionError(c.getGroup());
        if (!"Test Agent".equals(c.getHeaders().get("User-Agent"))) throw new AssertionError("UA");
        if (!"https://example.com/".equals(c.getHeaders().get("Referer"))) throw new AssertionError("Referer");
        System.out.println("M3UParser OK: " + c.getName() + " / " + c.getUrl());
    }
}
