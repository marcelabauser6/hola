import com.claimblocks.data.Claim;
import com.google.gson.JsonObject;
import java.util.UUID;

/**
 * Prueba de regresion de la logica de miembros y baneos que se ha tocado en Claim.java.
 * No necesita servidor: solo usa metodos puros del modelo de datos.
 */
public class ClaimLogicCheck {
    private static int fallos = 0;

    private static void check(String what, boolean cond) {
        System.out.printf("%-58s %s%n", what, cond ? "OK" : "FALLA");
        if (!cond) fallos++;
    }

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        UUID amigo = UUID.randomUUID();
        UUID malo = UUID.randomUUID();
        Claim c = new Claim(UUID.randomUUID(), owner, "Dueno", "claimstone_25x25",
                25, 15, "minecraft:overworld", 100, 64, 200);

        // --- miembros
        c.addMember(amigo, "Amigo");
        check("addMember registra el UUID", c.isMember(amigo));
        check("addMember registra el nombre", c.getMemberNames().contains("Amigo"));
        check("listas de UUID y nombres alineadas",
                c.getMembers().size() == c.getMemberNames().size());
        c.addMember(amigo, "Amigo");
        check("addMember no duplica", c.getMembers().size() == 1);

        // --- baneo
        c.addMember(malo, "Malo");
        check("dos miembros antes del baneo", c.getMembers().size() == 2);
        c.banPlayer(malo);
        check("banPlayer marca como baneado", c.isBanned(malo));
        check("banPlayer quita la membresia", !c.isMember(malo));
        check("banPlayer no toca a los demas miembros", c.isMember(amigo));
        check("listas siguen alineadas tras banear",
                c.getMembers().size() == c.getMemberNames().size());
        check("el nombre del baneado desaparece", !c.getMemberNames().contains("Malo"));
        c.unbanPlayer(malo);
        check("unbanPlayer levanta el baneo", !c.isBanned(malo));

        // --- persistencia (ida y vuelta)
        c.banPlayer(malo);
        c.getFlags().blockChestAccess = false;
        c.getFlags().welcomeMessage = "Bienvenido";
        JsonObject json = c.toJson();
        Claim back = Claim.fromJson(json);
        check("JSON conserva el dueno", owner.equals(back.getOwnerUUID()));
        check("JSON conserva los miembros", back.isMember(amigo));
        check("JSON conserva los nombres", back.getMemberNames().contains("Amigo"));
        check("JSON conserva los baneos", back.isBanned(malo));
        check("JSON conserva un flag apagado", !back.getFlags().blockChestAccess);
        check("JSON conserva el mensaje de bienvenida",
                "Bienvenido".equals(back.getFlags().welcomeMessage));
        check("JSON conserva radio y altura",
                back.getRadius() == 25 && back.getOwnHeight() == 15);
        check("el baneado no vuelve como miembro", !back.isMember(malo));

        System.out.println(fallos == 0 ? "\nRESULTADO: OK - " + "todas las comprobaciones pasan"
                                      : "\nRESULTADO: " + fallos + " FALLOS");
        if (fallos > 0) System.exit(1);
    }
}
