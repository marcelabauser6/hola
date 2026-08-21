import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Comprueba que el UUID que genera PlayerLookup en offline-mode es exactamente el mismo que
 * usa el servidor para un jugador sin autenticacion: MD5 de "OfflinePlayer:<nombre>".
 */
public class UuidCheck {
    public static void main(String[] args) {
        String[] names = {"Steve", "Alex", "marcelabauser6", "Jugador_123"};
        boolean ok = true;
        for (String name : names) {
            UUID viaMinecraft = UUIDUtil.m_235879_(name);
            UUID expected = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            boolean match = viaMinecraft.equals(expected);
            ok &= match;
            System.out.printf("%-16s UUIDUtil=%s  esperado=%s  %s%n",
                    name, viaMinecraft, expected, match ? "OK" : "FALLA");
        }
        System.out.println(ok ? "RESULTADO: OK - coincide con el UUID offline del servidor"
                              : "RESULTADO: FALLA");
        if (!ok) System.exit(1);
    }
}
