import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Offline bytecode catalogue. Does not load or execute any Minecraft/upstream class. */
public final class AuditGuActivations {
    static final String EXPECTED="b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc";
    static final String ITEMS="net/guzhenren/init/GuzhenrenModItems";
    static final Map<String,ClassNode> CLASSES=new TreeMap<>();
    static final Map<String,MethodNode> METHODS=new TreeMap<>();
    static String key(String owner,String name,String desc){return owner+"."+name+desc;}
    static String constructed(Handle handle){
        if(handle.getOwner().startsWith("net/guzhenren/item/"))return handle.getOwner();
        MethodNode method=METHODS.get(key(handle.getOwner(),handle.getName(),handle.getDesc()));
        if(method!=null)for(AbstractInsnNode node:method.instructions)
            if(node instanceof TypeInsnNode type && type.getOpcode()==Opcodes.NEW && type.desc.startsWith("net/guzhenren/item/"))return type.desc;
        return null;
    }
    static Map<String,Object> methodInfo(String owner,MethodNode method){
        List<Map<String,Object>> calls=new ArrayList<>(),signals=new ArrayList<>();
        Map<String,Integer> ordinals=new HashMap<>();
        Set<String> observedFields=new HashSet<>();
        int line=-1,index=0;
        for(AbstractInsnNode node:method.instructions){
            if(node instanceof LineNumberNode debug)line=debug.line;
            if(node instanceof MethodInsnNode call){
                String target=key(call.owner,call.name,call.desc);
                int ordinal=ordinals.merge(target,1,Integer::sum)-1;
                if(call.owner.startsWith("net/guzhenren/procedures/"))
                    calls.add(Map.of("target",target,"sourceLine",line,"asmInstructionIndex",index,"targetOrdinal",ordinal));
                if(call.owner.endsWith("/GuChongCDProcedure") && call.name.equals("execute"))
                    signals.add(Map.of("kind","cooldown-helper","target",target,"sourceLine",line,"asmInstructionIndex",index,"targetOrdinal",ordinal));
            }
            if(node instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD
                    && field.owner.equals("net/guzhenren/network/GuzhenrenModVariables$PlayerVariables")
                    && field.name.equals("zhenyuan"))
                signals.add(Map.of("kind","true-essence-field-write","sourceLine",line,"asmInstructionIndex",index,
                        "warning","Receiver and arithmetic must be checked; this may not be the actor or a debit."));
            if(node instanceof FieldInsnNode field
                    && field.owner.equals("net/guzhenren/network/GuzhenrenModVariables$PlayerVariables")){
                String kind=null;
                if(field.name.startsWith("ShaZhao")
                        && field.desc.equals("Lnet/minecraft/world/item/ItemStack;")
                        && (field.getOpcode()==Opcodes.GETFIELD || field.getOpcode()==Opcodes.PUTFIELD))
                    kind=field.getOpcode()==Opcodes.GETFIELD?"killer-move-gu-slot-read":"killer-move-gu-slot-write";
                else if(field.name.startsWith("ShaZhao") && field.name.endsWith("_NaiJiu")){
                    if(field.getOpcode()==Opcodes.GETFIELD)kind="killer-move-durability-guard-read";
                    else if(field.getOpcode()==Opcodes.PUTFIELD)kind="killer-move-durability-write";
                }else if(field.name.equals("sanzhao_CD") || field.name.equals("sanzhao_CD1")
                        || field.name.equals("sanzhao_CD2") || field.name.equals("sanzhao_CD3")){
                    if(field.getOpcode()==Opcodes.GETFIELD)kind="killer-move-cooldown-guard-read";
                    else if(field.getOpcode()==Opcodes.PUTFIELD)kind="killer-move-cooldown-write";
                }
                if(kind!=null && observedFields.add(kind+"|"+field.name))
                    signals.add(Map.of("kind",kind,"field",field.name,"opcode",field.getOpcode(),
                            "sourceLine",line,"asmInstructionIndex",index,
                            "warning","This field access is only a candidate signal; verify the branch and player receiver before calling it accepted."));
            }
            index++;
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("method",key(owner,method.name,method.desc));result.put("procedureCalls",calls);result.put("signals",signals);
        result.put("acceptedUseVerified",false);
        return result;
    }
    public static void main(String[] args)throws Exception{
        Path input=Path.of(".cache/guzhenren-0.1-neoforge-1.21.1.jar");
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        if(!EXPECTED.equals(sha))throw new IllegalStateException("Unsupported upstream fingerprint");
        Map<String,Set<Integer>> ranks=new TreeMap<>();
        List<String> nestedTags=new ArrayList<>();
        try(JarFile jar=new JarFile(input.toFile())){
            for(int rank=1;rank<=5;rank++){
                String path="data/guzhenren/tags/item/gushiguchong"+rank+".json";
                JarEntry entry=jar.getJarEntry(path);if(entry==null)throw new IllegalStateException("Missing rank tag "+path);
                try(Reader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
                    for(JsonElement value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")){
                        String id=value.isJsonPrimitive()?value.getAsString():value.getAsJsonObject().get("id").getAsString();
                        if(id.startsWith("#"))nestedTags.add(path+":"+id);
                        else ranks.computeIfAbsent(id,k->new TreeSet<>()).add(rank);
                    }
                }
            }
            for(JarEntry entry:Collections.list(jar.entries())){
                String name=entry.getName();
                if(!name.endsWith(".class") || !name.startsWith("net/guzhenren/"))continue;
                ClassNode node=new ClassNode();try(InputStream in=jar.getInputStream(entry)){new ClassReader(in).accept(node,ClassReader.SKIP_FRAMES);}
                CLASSES.put(node.name,node);
                for(MethodNode method:node.methods)METHODS.put(key(node.name,method.name,method.desc),method);
            }
        }
        Map<String,String> registered=new TreeMap<>();
        for(ClassNode type:CLASSES.values())if(type.name.startsWith(ITEMS))for(MethodNode method:type.methods)
            for(AbstractInsnNode node:method.instructions){
                if(!(node instanceof MethodInsnNode call) || !call.name.equals("register")
                        || !call.desc.startsWith("(Ljava/lang/String;Ljava/util/function/Supplier;)"))continue;
                String id=null,implementation=null;int steps=0;
                for(AbstractInsnNode prev=node.getPrevious();prev!=null && steps++<12;prev=prev.getPrevious()){
                    if(prev instanceof InvokeDynamicInsnNode dynamic)for(Object arg:dynamic.bsmArgs)
                        if(arg instanceof Handle handle){String found=constructed(handle);if(found!=null)implementation=found;}
                    if(prev instanceof LdcInsnNode ldc && ldc.cst instanceof String str){id=str;break;}
                }
                if(id!=null && implementation!=null)registered.put("guzhenren:"+id,implementation);
            }
        Set<String> entryNames=Set.of("use","useOn","hurtEnemy","inventoryTick","onEntitySwing","interactLivingEntity","mineBlock","finishUsingItem","releaseUsing");
        List<Map<String,Object>> gu=new ArrayList<>();
        int mapped=0;
        for(var tagged:ranks.entrySet()){
            Map<String,Object> item=new LinkedHashMap<>();item.put("itemId",tagged.getKey());item.put("rankTags",tagged.getValue());
            String implementation=registered.get(tagged.getKey());item.put("itemClass",implementation);
            List<Map<String,Object>> entryPoints=new ArrayList<>();
            if(implementation!=null){
                mapped++;ClassNode node=CLASSES.get(implementation);
                if(node!=null)for(MethodNode method:node.methods)if(entryNames.contains(method.name))
                    entryPoints.add(methodInfo(implementation,method));
            }
            item.put("wrapperEntryPoints",entryPoints);item.put("acceptedActivationHookVerified",false);gu.add(item);
        }
        List<Map<String,Object>> unclassified=new ArrayList<>();
        for(var entry:registered.entrySet()) {
            if(ranks.containsKey(entry.getKey())) continue;
            ClassNode type=CLASSES.get(entry.getValue());if(type==null)continue;
            List<Map<String,Object>> points=new ArrayList<>();
            for(MethodNode method:type.methods)if(entryNames.contains(method.name)){
                Map<String,Object> info=methodInfo(type.name,method);
                if(!((List<?>)info.get("procedureCalls")).isEmpty())points.add(info);
            }
            if(!points.isEmpty())unclassified.add(Map.of("itemId",entry.getKey(),"itemClass",entry.getValue(),
                    "wrapperEntryPoints",points,"isGuVerifiedByRankTag",false,"acceptedActivationHookVerified",false));
        }
        List<Map<String,Object>> signals=new ArrayList<>(),keys=new ArrayList<>();
        Map<String,Map<String,Object>> procedureSignals=new HashMap<>();
        for(ClassNode type:CLASSES.values())if(type.name.startsWith("net/guzhenren/procedures/"))
            for(MethodNode method:type.methods){
                Map<String,Object> info=methodInfo(type.name,method);
                if(!((List<?>)info.get("signals")).isEmpty()){
                    signals.add(info);
                    procedureSignals.put((String)info.get("method"),info);
                }
            }
        for(ClassNode type:CLASSES.values())if(type.name.startsWith("net/guzhenren/network/"))
            for(MethodNode method:type.methods){
            if(method.name.equals("pressAction")){
                Map<String,Object> info=methodInfo(type.name,method);
                List<Map<String,Object>> targets=new ArrayList<>();
                for(Object callValue:(List<?>)info.get("procedureCalls")){
                    Map<?,?> call=(Map<?,?>)callValue;
                    String target=(String)call.get("target");
                    Map<String,Object> targetInfo=procedureSignals.get(target);
                    if(targetInfo!=null)targets.add(Map.of("target",target,"signals",targetInfo.get("signals")));
                }
                info.put("targetProcedureSignals",targets);
                keys.add(info);
            }
        }
        List<Map<String,Object>> slotRefs=new ArrayList<>();
        String playerVariables="net/guzhenren/network/GuzhenrenModVariables$PlayerVariables";
        String itemStackDesc="Lnet/minecraft/world/item/ItemStack;";
        for(ClassNode type:CLASSES.values())for(MethodNode method:type.methods){
            Set<String> itemStackAccesses=new TreeSet<>(),durabilityAccesses=new TreeSet<>();
            for(AbstractInsnNode node:method.instructions)if(node instanceof FieldInsnNode field
                    && field.owner.equals(playerVariables)){
                String access=field.getOpcode()==Opcodes.GETFIELD?"GETFIELD":field.getOpcode()==Opcodes.PUTFIELD?"PUTFIELD":null;
                if(access==null)continue;
                if(field.name.matches("ShaZhao[1-4]_GuChong([1-9]|10)") && field.desc.equals(itemStackDesc))
                    itemStackAccesses.add(access+" "+field.name);
                else if(field.name.matches("ShaZhao[1-4]_GuChong([1-9]|10)_NaiJiu") && field.desc.equals("D"))
                    durabilityAccesses.add(access+" "+field.name);
            }
            if(itemStackAccesses.isEmpty())continue;
            Set<String> paired=new TreeSet<>();
            for(String itemAccess:itemStackAccesses){
                String slot=itemAccess.substring(itemAccess.indexOf(' ')+1);
                String durability=slot+"_NaiJiu";
                if(durabilityAccesses.stream().anyMatch(access->access.endsWith(" "+durability)))paired.add(slot);
            }
            slotRefs.add(Map.of("method",key(type.name,method.name,method.desc),
                    "itemStackAccesses",itemStackAccesses,"durabilityAccesses",durabilityAccesses,
                    "sameMethodPairedSlots",paired));
        }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("schema",1);report.put("upstreamSha256",sha);
        report.put("scope","Static bytecode discovery, not successful-use proof, canonical backlash policy, or runtime hook coverage.");
        report.put("taggedItemCount",gu.size());report.put("mappedItemCount",mapped);report.put("unresolvedNestedTags",nestedTags);
        report.put("items",gu);report.put("unclassifiedItemEntryCandidates",unclassified);report.put("registeredConcreteItemMappings",registered.size());report.put("procedureSignalCandidates",signals);report.put("keybindEntryCandidates",keys);report.put("killerMoveSlotReferenceCandidates",slotRefs);
        Path out=Path.of("build/gu-activation-audit/catalogue.json");Files.createDirectories(out.getParent());
        Files.writeString(out,new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create().toJson(report),StandardCharsets.UTF_8);
        System.out.println("Upstream fingerprint verified: "+sha);
        System.out.println("Catalogue: "+gu.size()+" tagged items, "+mapped+" registry-to-class mappings, "+signals.size()+" procedure signal methods, "+keys.size()+" keybind entry methods");
        System.out.println("Concrete registry mappings: "+registered.size()+", unclassified item candidates: "+unclassified.size()+". Rank tags alone do not enumerate all Gu.");
        long pairedMethods=slotRefs.stream().filter(method->!((Set<?>)method.get("sameMethodPairedSlots")).isEmpty()).count();
        System.out.println("Killer-move stack reference methods: "+slotRefs.size()+", same-method stack/durability pairs: "+pairedMethods+".");
        System.out.println("Unresolved nested tags: "+nestedTags.size()+"; accepted activation coverage remains unverified.");
        System.out.println(out.toAbsolutePath());
    }
}
