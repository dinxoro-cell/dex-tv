package com.dextv.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;

/** Lightweight local-only browser remote. Only runs while DeX TV is open. */
public final class RemoteServer {
    private static final int PORT = 8765;
    private final MainActivity activity;
    private final String pin;
    private final String session;
    private volatile boolean running;
    private ServerSocket server;
    private Thread thread;

    RemoteServer(MainActivity activity) {
        this.activity = activity;
        SecureRandom r = new SecureRandom();
        pin = String.format(Locale.US, "%06d", r.nextInt(1000000));
        session = Long.toHexString(r.nextLong()) + Long.toHexString(r.nextLong());
    }
    String getPin() { return pin; }
    String getAddressLabel() {
        String ip = address();
        return ip == null ? "Connect to Wi-Fi (port 8765)" : "http://" + ip + ":" + PORT;
    }
    private String address() {
        try {
            Enumeration<NetworkInterface> ni = NetworkInterface.getNetworkInterfaces();
            while (ni.hasMoreElements()) {
                NetworkInterface iface = ni.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<java.net.InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress() && !a.isLoopbackAddress())
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
    void start() {
        running = true;
        thread = new Thread(() -> {
            try (ServerSocket s = new ServerSocket(PORT)) {
                server = s;
                s.setSoTimeout(1500);
                while (running) {
                    try {
                        Socket client = s.accept();
                        client.setSoTimeout(3000);
                        new Thread(() -> handle(client), "dex-tv-remote-client").start();
                    } catch (SocketTimeoutException ignored) {}
                }
            } catch (Exception ignored) {}
        }, "dex-tv-remote-server");
        thread.setDaemon(true);
        thread.start();
    }
    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
    }
    private void handle(Socket socket) {
        try (Socket s = socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = s.getOutputStream();
            String request = in.readLine();
            if (request == null) return;
            String[] bits = request.split(" ");
            if (bits.length < 2) return;
            String path = bits[1];
            String method = bits[0];
            String cookie = "";
            String origin = "";
            String line;
            int headers = 0;
            while ((line = in.readLine()) != null && !line.isEmpty() && ++headers < 60) {
                String low = line.toLowerCase(Locale.ROOT);
                if (low.startsWith("cookie:")) cookie = line.substring(7).trim();
                if (low.startsWith("origin:")) origin = line.substring(7).trim();
            }
            String host = address();
            boolean paired = cookie.contains("dextv=" + session);
            if (path.startsWith("/pair?") && method.equals("POST")) {
                String submitted = param(path, "pin");
                if (pin.equals(submitted)) {
                    send(out, "200 OK", remotePage(true), "Set-Cookie: dextv=" + session + "; HttpOnly; SameSite=Strict; Path=/\r\n");
                } else send(out, "403 Forbidden", remotePage(false).replace("Enter the PIN", "Incorrect PIN. Enter the PIN"), "");
            } else if (path.equals("/") && method.equals("GET")) {
                send(out, "200 OK", remotePage(paired), "");
            } else if (path.startsWith("/cmd?") && method.equals("POST") && paired &&
                    (origin.isEmpty() || origin.equals("http://" + host + ":" + PORT))) {
                String key = param(path, "key");
                if (key.matches("left|right|up|down|ok|back|home|refresh")) {
                    activity.command(key);
                    send(out, "200 OK", "OK", "");
                } else send(out, "400 Bad Request", "Invalid", "");
            } else send(out, "403 Forbidden", "Pair your phone first", "");
        } catch (Exception ignored) {}
    }
    private String param(String path, String key) {
        int idx = path.indexOf('?');
        if (idx < 0) return "";
        for (String part : path.substring(idx + 1).split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return URLDecoder.decode(kv[1], "UTF-8"); } catch (Exception ignored) {}
            }
        }
        return "";
    }
    private void send(OutputStream out, String status, String body, String extra) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'self' 'unsafe-inline'\r\n" +
            extra + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }
    private String remotePage(boolean paired) {
        String css = "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>*{box-sizing:border-box}body{margin:0;background:linear-gradient(155deg,#172440,#080b18);color:#fff;font-family:Arial,sans-serif;min-height:100vh;display:flex;justify-content:center;align-items:center}" +
            "main{text-align:center;width:min(420px,95vw);padding:24px}h1{font-size:29px;margin-bottom:5px}p{color:#a7b6d0}" +
            "button{font:600 24px Arial;touch-action:manipulation;border:0;color:#f0f3fa;background:#293857;border-radius:20px;min-height:76px;cursor:pointer}" +
            "button:active{background:#688ad8} .pad{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:28px}" +
            "button.blank{visibility:hidden} .secondary{font-size:18px;margin-top:16px;width:47%;min-height:60px}" +
            "input{padding:15px;font-size:24px;letter-spacing:4px;max-width:100%;text-align:center;border-radius:12px;border:1px solid #7285a9;background:#111d36;color:white}" +
            ".go{font-size:18px;padding:12px 26px;margin-top:15px}</style>";
        if (!paired) return "<!doctype html><html><head><title>DeX TV Pairing</title>" + css +
            "</head><body><main><h1>DeX TV</h1><p>Enter the PIN shown on your TV</p>" +
            "<form action='/pair' method='post' onsubmit=\"this.action='/pair?pin='+encodeURIComponent(this.pin.value);\"><input name='pin' inputmode='numeric' autocomplete='off' maxlength='6' placeholder='6-digit PIN' required><br><button class='go' type='submit'>Connect Remote</button></form>" +
            "<p>Keep both devices on the same trusted Wi-Fi network.</p></main></body></html>";
        return "<!doctype html><html><head><title>DeX TV Remote</title>" + css +
            "</head><body><main><h1>DeX TV</h1><p>Wireless Remote</p>" +
            "<div class='pad'><button class='blank'> </button><button onclick=\"cmd('up')\">▲</button><button class='blank'> </button>" +
            "<button onclick=\"cmd('left')\">◀</button><button onclick=\"cmd('ok')\">OK</button><button onclick=\"cmd('right')\">▶</button>" +
            "<button class='blank'> </button><button onclick=\"cmd('down')\">▼</button><button class='blank'> </button></div>" +
            "<button class='secondary' onclick=\"cmd('back')\">Back</button> " +
            "<button class='secondary' onclick=\"cmd('home')\">Home</button><br>" +
            "<button class='secondary' onclick=\"cmd('refresh')\">Refresh Apps</button>" +
            "<p>This remote controls DeX TV's home screen, not other Android apps.</p>" +
            "</main><script>function cmd(k){fetch('/cmd?key='+k,{method:'POST',credentials:'same-origin'}).catch(()=>{});} " +
            "document.addEventListener('keydown',e=>{let k={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down',Enter:'ok',Escape:'back'}[e.key];if(k){e.preventDefault();cmd(k);}});</script></body></html>";
    }
}
