import stevku.jolt.internal.Server;
import stevku.jolt.utils.Logger;

void main() throws Exception
{
    Server server = new Server(4000)
        .onStart(() -> Logger.success("onStart"))
        .onStop(() -> Logger.warn("onStop"))
        .onStartAsync(() -> Logger.success("onStartAsync"));
    server.listen();
}