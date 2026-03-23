package io.velo.testmcp;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * MCP protocol test console — interactive web UI for testing all MCP endpoints.
 * <p>
 * Deployed as {@code /mcp-test/} and communicates with the MCP server
 * at {@code /ai-platform/mcp}.
 */
public class McpTestConsoleServlet extends HttpServlet {

    private static final String MCP_ENDPOINT = "/ai-platform/mcp";
    private static final String MCP_HEALTH = "/ai-platform/mcp/health";
    private static final String MCP_ADMIN = "/ai-platform/mcp/admin";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"ko\"><head><meta charset=\"UTF-8\">");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("<title>Velo MCP Test Console</title>");
        out.println("<style>");
        out.println(css());
        out.println("</style></head><body>");

        // Header
        out.println("<div class=\"header\">");
        out.println("  <h1>Velo MCP Test Console</h1>");
        out.println("  <p>MCP (Model Context Protocol) over Streamable HTTP — JSON-RPC 2.0 + SSE</p>");
        out.println("  <div class=\"endpoint-info\">");
        out.println("    <span class=\"badge\">POST " + MCP_ENDPOINT + "</span>");
        out.println("    <span class=\"badge sse\">GET " + MCP_ENDPOINT + " (SSE)</span>");
        out.println("    <span class=\"badge health\">GET " + MCP_HEALTH + "</span>");
        out.println("  </div>");
        out.println("</div>");

        // Health section
        out.println("<div class=\"section\">");
        out.println("  <h2>Health Check</h2>");
        out.println("  <button class=\"btn\" onclick=\"checkHealth()\">Check Health</button>");
        out.println("  <pre id=\"healthResult\" class=\"result\">Click to check...</pre>");
        out.println("</div>");

        // Initialize section
        out.println("<div class=\"section\">");
        out.println("  <h2>1. Initialize</h2>");
        out.println("  <div class=\"form-row\">");
        out.println("    <label>Client Name: <input id=\"clientName\" value=\"mcp-test-console\"></label>");
        out.println("    <label>Version: <input id=\"clientVersion\" value=\"1.0\"></label>");
        out.println("  </div>");
        out.println("  <button class=\"btn\" onclick=\"doInitialize()\">Initialize</button>");
        out.println("  <button class=\"btn secondary\" onclick=\"doNotifyInitialized()\">Notify Initialized</button>");
        out.println("  <div class=\"session-info\" id=\"sessionInfo\">No session</div>");
        out.println("  <pre id=\"initResult\" class=\"result\"></pre>");
        out.println("</div>");

        // Tools section
        out.println("<div class=\"section\">");
        out.println("  <h2>2. Tools</h2>");
        out.println("  <button class=\"btn\" onclick=\"listTools()\">tools/list</button>");
        out.println("  <pre id=\"toolsResult\" class=\"result\"></pre>");
        out.println("  <h3>tools/call</h3>");
        out.println("  <div class=\"form-row\">");
        out.println("    <label>Tool Name: <input id=\"toolName\" value=\"infer\"></label>");
        out.println("  </div>");
        out.println("  <label>Arguments (JSON):</label>");
        out.println("  <textarea id=\"toolArgs\" rows=\"3\">{\"prompt\": \"Hello, world!\", \"requestType\": \"CHAT\"}</textarea>");
        out.println("  <button class=\"btn\" onclick=\"callTool()\">Call Tool</button>");
        out.println("  <pre id=\"toolCallResult\" class=\"result\"></pre>");
        out.println("</div>");

        // Resources section
        out.println("<div class=\"section\">");
        out.println("  <h2>3. Resources</h2>");
        out.println("  <button class=\"btn\" onclick=\"listResources()\">resources/list</button>");
        out.println("  <div class=\"form-row\">");
        out.println("    <label>Resource URI: <input id=\"resourceUri\" value=\"mcp://models\"></label>");
        out.println("    <button class=\"btn secondary\" onclick=\"readResource()\">resources/read</button>");
        out.println("  </div>");
        out.println("  <pre id=\"resourcesResult\" class=\"result\"></pre>");
        out.println("</div>");

        // Prompts section
        out.println("<div class=\"section\">");
        out.println("  <h2>4. Prompts</h2>");
        out.println("  <button class=\"btn\" onclick=\"listPrompts()\">prompts/list</button>");
        out.println("  <div class=\"form-row\">");
        out.println("    <label>Prompt Name: <input id=\"promptName\" value=\"chat\"></label>");
        out.println("  </div>");
        out.println("  <label>Arguments (JSON):</label>");
        out.println("  <textarea id=\"promptArgs\" rows=\"2\">{\"task\": \"Explain microservices\", \"language\": \"Korean\"}</textarea>");
        out.println("  <button class=\"btn secondary\" onclick=\"getPrompt()\">prompts/get</button>");
        out.println("  <pre id=\"promptsResult\" class=\"result\"></pre>");
        out.println("</div>");

        // SSE section
        out.println("<div class=\"section\">");
        out.println("  <h2>5. SSE Stream</h2>");
        out.println("  <button class=\"btn\" onclick=\"connectSse()\">Connect SSE</button>");
        out.println("  <button class=\"btn secondary\" onclick=\"disconnectSse()\">Disconnect</button>");
        out.println("  <pre id=\"sseResult\" class=\"result\">Not connected</pre>");
        out.println("</div>");

        // Ping section
        out.println("<div class=\"section\">");
        out.println("  <h2>6. Ping</h2>");
        out.println("  <button class=\"btn\" onclick=\"doPing()\">Ping</button>");
        out.println("  <pre id=\"pingResult\" class=\"result\"></pre>");
        out.println("</div>");

        // Admin section
        out.println("<div class=\"section\">");
        out.println("  <h2>7. Admin API</h2>");
        out.println("  <div class=\"btn-group\">");
        out.println("    <button class=\"btn\" onclick=\"adminGet('servers')\">Servers</button>");
        out.println("    <button class=\"btn\" onclick=\"adminGet('tools')\">Tools</button>");
        out.println("    <button class=\"btn\" onclick=\"adminGet('resources')\">Resources</button>");
        out.println("    <button class=\"btn\" onclick=\"adminGet('prompts')\">Prompts</button>");
        out.println("    <button class=\"btn\" onclick=\"adminGet('sessions')\">Sessions</button>");
        out.println("    <button class=\"btn\" onclick=\"adminGet('audit?limit=10')\">Audit</button>");
        out.println("    <button class=\"btn\" onclick=\"adminGet('policies')\">Policies</button>");
        out.println("  </div>");
        out.println("  <pre id=\"adminResult\" class=\"result\"></pre>");
        out.println("</div>");

        // JavaScript
        out.println("<script>");
        out.println(javascript());
        out.println("</script>");
        out.println("</body></html>");
    }

    private static String css() {
        return """
            :root{--bg:#f4efe6;--surface:#fff;--text:#1a1a1a;--soft:#6b7280;--border:#e5e7eb;--primary:#0f766e;--primary-dark:#134e4a;--radius:10px;}
            *{box-sizing:border-box;margin:0;padding:0;}
            body{font-family:'Pretendard','Noto Sans KR',system-ui,sans-serif;color:var(--text);background:var(--bg);padding:24px;max-width:900px;margin:0 auto;}
            .header{background:linear-gradient(135deg,var(--primary-dark),var(--primary));color:#fff;border-radius:var(--radius);padding:28px;margin-bottom:20px;}
            .header h1{font-size:24px;margin-bottom:6px;}
            .header p{font-size:13px;opacity:0.8;margin-bottom:12px;}
            .endpoint-info{display:flex;flex-wrap:wrap;gap:8px;}
            .badge{padding:5px 12px;border-radius:20px;font-size:11px;font-weight:600;background:rgba(255,255,255,0.15);border:1px solid rgba(255,255,255,0.2);}
            .badge.sse{background:rgba(139,92,246,0.25);border-color:rgba(139,92,246,0.4);}
            .badge.health{background:rgba(34,197,94,0.25);border-color:rgba(34,197,94,0.4);}
            .section{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;margin-bottom:16px;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
            .section h2{font-size:16px;margin-bottom:12px;color:var(--primary-dark);}
            .section h3{font-size:14px;margin:12px 0 8px;color:var(--soft);}
            .form-row{display:flex;gap:12px;margin-bottom:10px;flex-wrap:wrap;}
            .form-row label{display:flex;align-items:center;gap:6px;font-size:13px;font-weight:600;color:var(--soft);}
            input,textarea{padding:8px 12px;border:1px solid var(--border);border-radius:6px;font-size:13px;font-family:inherit;}
            input{width:200px;}
            textarea{width:100%;font-family:'JetBrains Mono',Consolas,monospace;margin-bottom:8px;}
            .btn{padding:8px 16px;border:0;border-radius:6px;font-size:13px;font-weight:600;cursor:pointer;background:var(--primary);color:#fff;transition:all 0.15s;margin-right:6px;margin-bottom:6px;}
            .btn:hover{background:var(--primary-dark);}
            .btn.secondary{background:#f1f5f9;color:var(--text);border:1px solid var(--border);}
            .btn.secondary:hover{background:#e2e8f0;}
            .btn-group{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px;}
            .result{background:#1e293b;color:#e2e8f0;border-radius:var(--radius);padding:14px;font-family:'JetBrains Mono',Consolas,monospace;font-size:12px;line-height:1.6;overflow:auto;max-height:300px;white-space:pre-wrap;margin-top:10px;}
            .session-info{padding:8px 14px;border-radius:6px;font-size:12px;font-weight:600;margin:10px 0;background:#f0fdf4;color:var(--primary);border:1px solid #bbf7d0;}
            """;
    }

    private static String javascript() {
        return """
            const MCP='""" + MCP_ENDPOINT + """
            ';
            const ADMIN='""" + MCP_ADMIN + """
            ';
            let sessionId=null;
            let reqId=1;
            let sseSource=null;

            function show(id,text){
              const el=document.getElementById(id);
              try{el.textContent=JSON.stringify(JSON.parse(text),null,2);}
              catch(e){el.textContent=text;}
            }

            async function rpc(method,params){
              const body={jsonrpc:'2.0',id:reqId++,method:method};
              if(params)body.params=params;
              const headers={'Content-Type':'application/json'};
              if(sessionId)headers['Mcp-Session-Id']=sessionId;
              const resp=await fetch(MCP,{method:'POST',headers:headers,body:JSON.stringify(body)});
              const sid=resp.headers.get('Mcp-Session-Id');
              if(sid)sessionId=sid;
              updateSessionInfo();
              if(resp.status===204)return '204 No Content';
              return await resp.text();
            }

            function updateSessionInfo(){
              document.getElementById('sessionInfo').textContent=
                sessionId?'Session: '+sessionId:'No session';
            }

            // Health
            async function checkHealth(){
              try{const r=await fetch('""" + MCP_HEALTH + """
            ');show('healthResult',await r.text());}
              catch(e){show('healthResult','Error: '+e.message);}
            }

            // Initialize
            async function doInitialize(){
              const r=await rpc('initialize',{
                protocolVersion:'2024-11-05',
                capabilities:{},
                clientInfo:{name:document.getElementById('clientName').value,
                            version:document.getElementById('clientVersion').value}
              });
              show('initResult',r);
            }

            async function doNotifyInitialized(){
              const r=await rpc('notifications/initialized');
              show('initResult',r);
            }

            // Tools
            async function listTools(){show('toolsResult',await rpc('tools/list'));}

            async function callTool(){
              let args;
              try{args=JSON.parse(document.getElementById('toolArgs').value);}
              catch(e){show('toolCallResult','Invalid JSON: '+e.message);return;}
              show('toolCallResult',await rpc('tools/call',{
                name:document.getElementById('toolName').value,
                arguments:args
              }));
            }

            // Resources
            async function listResources(){show('resourcesResult',await rpc('resources/list'));}
            async function readResource(){
              show('resourcesResult',await rpc('resources/read',{
                uri:document.getElementById('resourceUri').value
              }));
            }

            // Prompts
            async function listPrompts(){show('promptsResult',await rpc('prompts/list'));}
            async function getPrompt(){
              let args;
              try{args=JSON.parse(document.getElementById('promptArgs').value);}
              catch(e){show('promptsResult','Invalid JSON: '+e.message);return;}
              show('promptsResult',await rpc('prompts/get',{
                name:document.getElementById('promptName').value,
                arguments:args
              }));
            }

            // SSE
            function connectSse(){
              if(sseSource)sseSource.close();
              const url=MCP+(sessionId?'?sessionId='+sessionId:'');
              sseSource=new EventSource(url);
              const el=document.getElementById('sseResult');
              el.textContent='Connecting...\\n';
              sseSource.onopen=()=>{el.textContent+='Connected!\\n';};
              sseSource.onmessage=(e)=>{el.textContent+='data: '+e.data+'\\n';};
              sseSource.addEventListener('endpoint',(e)=>{el.textContent+='endpoint: '+e.data+'\\n';});
              sseSource.onerror=(e)=>{el.textContent+='Error / disconnected\\n';};
            }
            function disconnectSse(){
              if(sseSource){sseSource.close();sseSource=null;}
              document.getElementById('sseResult').textContent='Disconnected';
            }

            // Ping
            async function doPing(){show('pingResult',await rpc('ping'));}

            // Admin
            async function adminGet(sub){
              try{const r=await fetch(ADMIN+'/'+sub);show('adminResult',await r.text());}
              catch(e){show('adminResult','Error: '+e.message);}
            }

            // Auto health check on load
            checkHealth();
            """;
    }
}
