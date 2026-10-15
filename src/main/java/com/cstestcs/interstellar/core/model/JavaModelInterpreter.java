package com.cstestcs.interstellar.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interprets the {@code createBodyLayer()}-style method of a Blockbench "Modded Entity" Java export (Mojang mappings)
 * without compiling it. Only the model-building subset of Java is understood:
 * MeshDefinition / PartDefinition / CubeListBuilder / PartPose / CubeDeformation / LayerDefinition calls, local
 * variables, float arithmetic, casts and Math/Mth constants. Everything else in the file (imports, the class, setupAnim,
 * render methods) is ignored, so any Blockbench export can be dropped in unchanged.
 */
public final class JavaModelInterpreter {
    public static final class ModelParseException extends Exception {
        public ModelParseException(String msg) {
            super(msg);
        }
    }

    private final List<Token> tokens;
    private int pos;
    private final Map<String, Object> vars = new HashMap<>();
    private ModelDef result;

    private JavaModelInterpreter(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static ModelDef interpret(String source) throws ModelParseException {
        List<Token> tokens = Lexer.lex(source);
        int start = findBuilderMethod(tokens);
        if (start < 0) throw new ModelParseException("no method returning LayerDefinition found");
        JavaModelInterpreter in = new JavaModelInterpreter(tokens);
        in.pos = start;
        in.runBlock();
        if (in.result == null) throw new ModelParseException("method never returned LayerDefinition.create(...)");
        return in.result;
    }

    /** Finds the '{' that opens the first {@code static LayerDefinition name(...)} method body. */
    private static int findBuilderMethod(List<Token> t) {
        for (int i = 0; i + 3 < t.size(); i++) {
            if (t.get(i).is("LayerDefinition") && t.get(i + 1).kind == Kind.IDENT && t.get(i + 2).is("(")) {
                int depth = 0;
                for (int j = i + 2; j < t.size(); j++) {
                    if (t.get(j).is("(")) depth++;
                    else if (t.get(j).is(")") && --depth == 0) {
                        if (j + 1 < t.size() && t.get(j + 1).is("{")) return j + 2;
                        break;
                    }
                }
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ statements

    private void runBlock() throws ModelParseException {
        while (pos < tokens.size() && result == null) {
            Token t = peek();
            if (t.is("}")) return;
            if (t.is("return")) {
                pos++;
                Object v = expression();
                if (v instanceof ModelDef def) result = def;
                skipTo(";");
                return;
            }
            statement();
        }
    }

    private void statement() throws ModelParseException {
        while (peek().is("final")) pos++;
        // Declaration: Type [<...>] name = expr ;
        int save = pos;
        if (peek().kind == Kind.IDENT) {
            int p = pos + 1;
            if (p < tokens.size() && tokens.get(p).is("<")) p = skipGenerics(p);
            if (p + 1 < tokens.size() && tokens.get(p).kind == Kind.IDENT && tokens.get(p + 1).is("=")) {
                String name = tokens.get(p).text;
                pos = p + 2;
                vars.put(name, expression());
                expect(";");
                return;
            }
            if (peekAt(1).is("=")) {
                String name = peek().text;
                pos += 2;
                vars.put(name, expression());
                expect(";");
                return;
            }
        }
        pos = save;
        expression();
        if (peek().is(";")) pos++;
        else skipTo(";");
    }

    private int skipGenerics(int p) {
        int depth = 0;
        for (; p < tokens.size(); p++) {
            if (tokens.get(p).is("<")) depth++;
            else if (tokens.get(p).is(">") && --depth == 0) return p + 1;
        }
        return p;
    }

    // ------------------------------------------------------------------ expressions

    private Object expression() throws ModelParseException {
        Object left = term();
        while (peek().is("+") || peek().is("-")) {
            boolean plus = next().is("+");
            Object right = term();
            left = plus ? num(left) + num(right) : num(left) - num(right);
        }
        return left;
    }

    private Object term() throws ModelParseException {
        Object left = unary();
        while (peek().is("*") || peek().is("/")) {
            boolean mul = next().is("*");
            Object right = unary();
            left = mul ? num(left) * num(right) : num(left) / num(right);
        }
        return left;
    }

    private Object unary() throws ModelParseException {
        if (peek().is("-")) {
            pos++;
            return -num(unary());
        }
        if (peek().is("+")) {
            pos++;
            return num(unary());
        }
        // cast: (float) / (double) / (int)
        if (peek().is("(") && peekAt(1).kind == Kind.IDENT && peekAt(2).is(")")
                && (peekAt(1).is("float") || peekAt(1).is("double") || peekAt(1).is("int"))) {
            boolean toInt = peekAt(1).is("int");
            pos += 3;
            double v = num(unary());
            return toInt ? (double) (int) v : v;
        }
        return postfix(primary());
    }

    private Object primary() throws ModelParseException {
        Token t = next();
        switch (t.kind) {
            case NUMBER:
                return t.number;
            case STRING:
                return t.text;
            case PUNCT:
                if (t.is("(")) {
                    Object v = expression();
                    expect(")");
                    return v;
                }
                throw err("unexpected '" + t.text + "'");
            case IDENT:
            default:
                if (t.is("true")) return Boolean.TRUE;
                if (t.is("false")) return Boolean.FALSE;
                if (t.is("new")) return construct();
                if (vars.containsKey(t.text)) return vars.get(t.text);
                return new ClassRef(t.text);
        }
    }

    private Object construct() throws ModelParseException {
        String type = next().text;
        if (peek().is("<")) pos = skipGenerics(pos);
        List<Object> args = args();
        switch (type) {
            case "MeshDefinition":
                return new ModelDef.PartDef("root");
            case "CubeDeformation":
                if (args.size() == 1) return grow(num(args.get(0)), num(args.get(0)), num(args.get(0)));
                if (args.size() == 3) return grow(num(args.get(0)), num(args.get(1)), num(args.get(2)));
                return grow(0, 0, 0);
            case "ModelLayerLocation":
            case "ResourceLocation":
            case "Identifier":
                return null;
            default:
                throw err("cannot construct " + type);
        }
    }

    private Object postfix(Object target) throws ModelParseException {
        while (peek().is(".")) {
            pos++;
            String member = next().text;
            if (peek().is("(")) {
                target = call(target, member, args());
            } else {
                target = field(target, member);
            }
        }
        return target;
    }

    private List<Object> args() throws ModelParseException {
        expect("(");
        List<Object> out = new ArrayList<>();
        if (peek().is(")")) {
            pos++;
            return out;
        }
        while (true) {
            out.add(expression());
            if (peek().is(",")) {
                pos++;
                continue;
            }
            expect(")");
            return out;
        }
    }

    private Object field(Object target, String name) throws ModelParseException {
        if (target instanceof ClassRef c) {
            String q = c.name + "." + name;
            switch (q) {
                case "Math.PI", "Mth.PI":
                    return Math.PI;
                case "Mth.HALF_PI":
                    return Math.PI / 2;
                case "Mth.TWO_PI":
                    return Math.PI * 2;
                case "Mth.DEG_TO_RAD":
                    return Math.PI / 180;
                case "Mth.RAD_TO_DEG":
                    return 180 / Math.PI;
                case "PartPose.ZERO":
                    return new Pose();
                case "CubeDeformation.NONE":
                    return grow(0, 0, 0);
                default:
                    // Fully-qualified names like net.minecraft.client.model.geom.PartPose
                    return new ClassRef(name);
            }
        }
        throw err("unknown field " + name);
    }

    private Object call(Object target, String m, List<Object> a) throws ModelParseException {
        if (target instanceof ClassRef c) {
            switch (c.name + "." + m) {
                case "CubeListBuilder.create":
                    return new CubeList();
                case "PartPose.offset":
                    return pose(a, true, false);
                case "PartPose.rotation":
                    return pose(a, false, true);
                case "PartPose.offsetAndRotation":
                    return pose(a, true, true);
                case "LayerDefinition.create": {
                    ModelDef.PartDef root = (ModelDef.PartDef) a.get(0);
                    return new ModelDef(a.size() > 1 ? (int) num(a.get(1)) : 64, a.size() > 2 ? (int) num(a.get(2)) : 64, root);
                }
                case "Math.toRadians":
                    return Math.toRadians(num(a.get(0)));
                case "Math.toDegrees":
                    return Math.toDegrees(num(a.get(0)));
                case "Math.sin", "Mth.sin":
                    return Math.sin(num(a.get(0)));
                case "Math.cos", "Mth.cos":
                    return Math.cos(num(a.get(0)));
                case "Float.valueOf":
                    return num(a.get(0));
                default:
                    throw err("unsupported call " + c.name + "." + m);
            }
        }
        if (target instanceof ModelDef.PartDef part) {
            switch (m) {
                case "getRoot":
                    return part;
                case "addOrReplaceChild": {
                    ModelDef.PartDef child = new ModelDef.PartDef((String) a.get(0));
                    if (a.size() > 1 && a.get(1) instanceof CubeList list) child.cubes.addAll(list.cubes);
                    if (a.size() > 2 && a.get(2) instanceof Pose p) p.applyTo(child);
                    part.children.put(child.name, child);
                    return child;
                }
                case "getChild":
                    return part.children.get((String) a.get(0));
                case "clearChild", "clearRecursively", "retainExactParts", "retainPartsAndChildren":
                    return part;
                default:
                    throw err("unsupported PartDefinition." + m);
            }
        }
        if (target instanceof CubeList list) {
            switch (m) {
                case "texOffs":
                    list.u = (int) num(a.get(0));
                    list.v = (int) num(a.get(1));
                    return list;
                case "mirror":
                    list.mirror = a.isEmpty() || Boolean.TRUE.equals(a.get(0));
                    return list;
                case "addBox":
                    list.addBox(a);
                    return list;
                default:
                    // extend(), uvMapping etc. are not used by Blockbench exports.
                    return list;
            }
        }
        if (target instanceof Pose p) {
            if (m.equals("withScale") || m.equals("scaled")) {
                p.scale = (float) num(a.get(0));
                return p;
            }
            return p;
        }
        if (target instanceof float[] g && m.equals("extend")) {
            double e = num(a.get(0));
            return grow(g[0] + e, g[1] + e, g[2] + e);
        }
        if (target instanceof ModelDef def) return def; // e.g. .apply(MeshTransformer)
        throw err("cannot call " + m + " on " + (target == null ? "null" : target.getClass().getSimpleName()));
    }

    private Pose pose(List<Object> a, boolean offset, boolean rotation) {
        Pose p = new Pose();
        int i = 0;
        if (offset) {
            p.x = (float) num(a.get(i++));
            p.y = (float) num(a.get(i++));
            p.z = (float) num(a.get(i++));
        }
        if (rotation) {
            p.xRot = (float) num(a.get(i++));
            p.yRot = (float) num(a.get(i++));
            p.zRot = (float) num(a.get(i));
        }
        return p;
    }

    private static float[] grow(double x, double y, double z) {
        return new float[] { (float) x, (float) y, (float) z };
    }

    static double num(Object o) {
        if (o instanceof Double d) return d;
        if (o instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException("not a number: " + o);
    }

    // ------------------------------------------------------------------ helper types

    private record ClassRef(String name) {}

    private static final class Pose {
        float x, y, z, xRot, yRot, zRot, scale = 1;

        void applyTo(ModelDef.PartDef p) {
            p.x = x;
            p.y = y;
            p.z = z;
            p.xRot = xRot;
            p.yRot = yRot;
            p.zRot = zRot;
            p.scale = scale;
        }
    }

    private static final class CubeList {
        final List<ModelDef.CubeDef> cubes = new ArrayList<>();
        int u, v;
        boolean mirror;

        void addBox(List<Object> raw) {
            List<Object> a = new ArrayList<>(raw);
            if (!a.isEmpty() && a.get(0) instanceof String) a.remove(0); // named box
            float[] grow = { 0, 0, 0 };
            boolean mir = mirror;
            int bu = u, bv = v;
            if (a.size() > 6) {
                Object extra = a.get(6);
                if (extra instanceof float[] g) grow = g;
                else if (extra instanceof Boolean b) mir = b;
                else if (extra instanceof Double d) grow = new float[] { d.floatValue(), d.floatValue(), d.floatValue() };
                // Named-box overload: (name, x,y,z, w,h,d, deformation, u, v)
                if (a.size() == 9 && raw.get(0) instanceof String) {
                    bu = (int) num(a.get(7));
                    bv = (int) num(a.get(8));
                }
            }
            cubes.add(new ModelDef.CubeDef(bu, bv,
                    (float) num(a.get(0)), (float) num(a.get(1)), (float) num(a.get(2)),
                    (float) num(a.get(3)), (float) num(a.get(4)), (float) num(a.get(5)),
                    grow[0], grow[1], grow[2], mir));
        }
    }

    // ------------------------------------------------------------------ token plumbing

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : Token.EOF;
    }

    private Token peekAt(int off) {
        return pos + off < tokens.size() ? tokens.get(pos + off) : Token.EOF;
    }

    private Token next() throws ModelParseException {
        if (pos >= tokens.size()) throw err("unexpected end of file");
        return tokens.get(pos++);
    }

    private void expect(String s) throws ModelParseException {
        Token t = next();
        if (!t.is(s)) throw err("expected '" + s + "' but found '" + t.text + "'");
    }

    private void skipTo(String s) {
        while (pos < tokens.size() && !tokens.get(pos).is(s)) pos++;
        pos++;
    }

    private ModelParseException err(String msg) {
        int line = pos < tokens.size() ? tokens.get(Math.max(0, pos - 1)).line : -1;
        return new ModelParseException("line " + line + ": " + msg);
    }

    enum Kind { IDENT, NUMBER, STRING, PUNCT, EOF }

    record Token(Kind kind, String text, double number, int line) {
        static final Token EOF = new Token(Kind.EOF, "<eof>", 0, -1);

        boolean is(String s) {
            return kind != Kind.STRING && text.equals(s);
        }
    }

    static final class Lexer {
        static List<Token> lex(String s) {
            List<Token> out = new ArrayList<>();
            int i = 0, line = 1, n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\n') {
                    line++;
                    i++;
                } else if (Character.isWhitespace(c)) {
                    i++;
                } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    while (i < n && s.charAt(i) != '\n') i++;
                } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                        if (s.charAt(i) == '\n') line++;
                        i++;
                    }
                    i += 2;
                } else if (c == '"') {
                    StringBuilder sb = new StringBuilder();
                    i++;
                    while (i < n && s.charAt(i) != '"') {
                        if (s.charAt(i) == '\\' && i + 1 < n) i++;
                        sb.append(s.charAt(i++));
                    }
                    i++;
                    out.add(new Token(Kind.STRING, sb.toString(), 0, line));
                } else if (c == '\'') {
                    i += 3; // char literal, irrelevant
                } else if (Character.isDigit(c) || c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
                    int st = i;
                    while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E'
                            || (s.charAt(i) == '-' || s.charAt(i) == '+') && (s.charAt(i - 1) == 'e' || s.charAt(i - 1) == 'E'))) i++;
                    String num = s.substring(st, i);
                    if (i < n && "fFdDlL".indexOf(s.charAt(i)) >= 0) i++;
                    out.add(new Token(Kind.NUMBER, num, Double.parseDouble(num), line));
                } else if (Character.isJavaIdentifierStart(c)) {
                    int st = i;
                    while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                    out.add(new Token(Kind.IDENT, s.substring(st, i), 0, line));
                } else {
                    out.add(new Token(Kind.PUNCT, String.valueOf(c), 0, line));
                    i++;
                }
            }
            return out;
        }
    }
}
