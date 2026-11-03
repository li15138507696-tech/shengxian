import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Offline read-side audit of upstream kongqiao/renqi/jieduan/shiqiao field GETFIELDs. */
public final class AuditUpstreamReads {
    static final String EXPECTED = "b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc";
    static final String VARS = "net/guzhenren/network/GuzhenrenModVariables$PlayerVariables";
    static final List<String> FIELDS = List.of("kongqiao", "renqi", "jieduan", "shiqiao");

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        try (JarFile archive = new JarFile(jar.toFile())) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.digest(Files.readAllBytes(jar));
            var enumeration = archive.entries();
            Map<String, List<Map<String, Object>>> reads = new TreeMap<>();
            int classes = 0;
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                new ClassReader(archive.getInputStream(entry)).accept(node, ClassReader.SKIP_FRAMES);
                classes++;
                for (MethodNode method : node.methods) {
                    int line = -1;
                    List<AbstractInsnNode> window = new ArrayList<>();
                    for (AbstractInsnNode node2 : method.instructions) {
                        if (node2 instanceof LineNumberNode ln) line = ln.line;
                        window.add(node2);
                        if (window.size() > 14) window.remove(0);
                        if (node2 instanceof FieldInsnNode field
                                && field.getOpcode() == Opcodes.GETFIELD
                                && field.owner.equals(VARS)
                                && FIELDS.contains(field.name)) {
                            String after = contextAfter(node2, 10);
                            reads.computeIfAbsent(field.name, k -> new ArrayList<>()).add(Map.of(
                                    "class", node.name,
                                    "method", method.name + method.desc,
                                    "line", line,
                                    "followedBy", after));
                        }
                    }
                }
            }
            System.out.println("classes=" + classes);
            for (String name : FIELDS) {
                List<Map<String, Object>> rows = reads.getOrDefault(name, List.of());
                Set<String> owners = new TreeSet<>();
                for (Map<String, Object> row : rows) owners.add((String) row.get("class"));
                System.out.println("FIELD " + name + " reads=" + rows.size() + " classes=" + owners.size());
                int shown = 0;
                for (Map<String, Object> row : rows) {
                    if (shown++ >= 40) { System.out.println("  ... more"); break; }
                    System.out.println("  " + row.get("class") + "#" + row.get("method")
                            + " line=" + row.get("line") + " after=" + row.get("followedBy"));
                }
            }
        }
    }

    /** Summarizes the instructions that consume the read value: branch/cmp/display hints. */
    static String contextAfter(AbstractInsnNode start, int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AbstractInsnNode node = start.getNext(); node != null && count < limit; node = node.getNext()) {
            if (node instanceof LineNumberNode) continue;
            int op = node.getOpcode();
            if (op == -1) continue;
            String hint;
            if (node instanceof LdcInsnNode ldc) { hint = "ldc:" + String.valueOf(ldc.cst).replace(" ", "").substring(0, Math.min(18, String.valueOf(ldc.cst).length())); }
            else if (node instanceof MethodInsnNode m) hint = "call:" + m.owner.substring(m.owner.lastIndexOf('/') + 1) + "." + m.name;
            else if (node instanceof FieldInsnNode fn) hint = (fn.getOpcode() == Opcodes.GETFIELD ? "get:" : "put:") + fn.name;
            else if (node instanceof JumpInsnNode j) hint = "jump" + j.getOpcode();
            else if (node instanceof VarInsnNode v) hint = "store" + v.var;
            else if (node instanceof IntInsnNode ii) hint = "const" + ii.operand;
            else if (node instanceof InsnNode in) hint = "op" + in.getOpcode();
            else hint = "op" + op;
            sb.append(hint).append(" ");
            count++;
        }
        String text = sb.toString().trim();
        return text.length() > 90 ? text.substring(0, 90) : text;
    }
}
