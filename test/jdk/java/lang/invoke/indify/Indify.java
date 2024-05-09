/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package indify;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.nio.file.Files;
import java.util.*;
import java.io.*;
import java.lang.reflect.Modifier;
import java.util.regex.*;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.DirectMethodHandleDesc.Kind.*;

/**
 * Transform one or more class files to incorporate JSR 292 features,
 * such as {@code invokedynamic}.
 * <p>
 * This is a standalone program in a single source file.
 * In this form, it may be useful for test harnesses, small experiments, and javadoc examples.
 * Copies of this file may show up in multiple locations for standalone usage.
 * The primary maintained location of this file is as follows:
 * <a href="http://kenai.com/projects/ninja/sources/indify-repo/content/src/indify/Indify.java">
 * http://kenai.com/projects/ninja/sources/indify-repo/content/src/indify/Indify.java</a>
 * <p>
 * Static private methods named MH_x and MT_x (where x is arbitrary)
 * must be stereotyped generators of MethodHandle and MethodType
 * constants.  All calls to them are transformed to {@code CONSTANT_MethodHandle}
 * and {@code CONSTANT_MethodType} "ldc" instructions.
 * The stereotyped code must create method types by calls to {@code methodType} or
 * {@code fromMethodDescriptorString}.  The "lookup" argument must be created
 * by calls to {@code java.lang.invoke.MethodHandles#lookup MethodHandles.lookup}.
 * The class and string arguments must be constant.
 * The following methods of {@code java.lang.invoke.MethodHandle.Lookup Lookup} are
 * allowed for method handle creation: {@code findStatic}, {@code findVirtual},
 * {@code findConstructor}, {@code findSpecial},
 * {@code findGetter}, {@code findSetter},
 * {@code findStaticGetter}, or {@code findStaticSetter}.
 * The call to one of these methods must be followed immediately
 * by an {@code areturn} instruction.
 * The net result of the call to the MH_x or MT_x method must be
 * the creation of a constant method handle.  Thus, replacing calls
 * to MH_x or MT_x methods by {@code ldc} instructions should leave
 * the meaning of the program unchanged.
 * <p>
 * Static private methods named INDY_x must be stereotyped generators
 * of {@code invokedynamic} call sites.
 * All calls to them must be immediately followed by
 * {@code invokeExact} calls.
 * All such pairs of calls are transformed to {@code invokedynamic}
 * instructions.  Each INDY_x method must begin with a call to a
 * MH_x method, which is taken to be its bootstrap method.
 * The method must be immediately invoked (via {@code invokeGeneric}
 * on constant lookup, name, and type arguments.  An object array of
 * constants may also be appended to the {@code invokeGeneric call}.
 * This call must be cast to {@code CallSite}, and the result must be
 * immediately followed by a call to {@code dynamicInvoker}, with the
 * resulting method handle returned.
 * <p>
 * The net result of all of these actions is equivalent to the JVM's
 * execution of an {@code invokedynamic} instruction in the unlinked state.
 * Running this code once should produce the same results as running
 * the corresponding {@code invokedynamic} instruction.
 * In order to model the caching behavior, the code of an INDY_x
 * method is allowed to begin with getstatic, aaload, and if_acmpne
 * instructions which load a static method handle value and return it
 * if the value is non-null.
 * <p>
 * Example usage:
 * <blockquote><pre>
$ JAVA_HOME=(some recent OpenJDK 7 build)
$ ant
$ $JAVA_HOME/bin/java -cp build/classes indify.Indify --overwrite --dest build/testout build/classes/indify/Example.class
$ $JAVA_HOME/bin/java -cp build/classes indify.Example
MT = (java.lang.Object)java.lang.Object
MH = adder(int,int)java.lang.Integer
adder(1,2) = 3
calling indy:  42
$ $JAVA_HOME/bin/java -cp build/testout indify.Example
(same output as above)
 * </pre></blockquote>
 * <p>
 * A version of this transformation built on top of <a href="http://asm.ow2.org/">http://asm.ow2.org/</a> would be welcome.
 * @author John Rose
 */
public class Indify {
    public static void main(String... av) throws IOException {
        new Indify().run(av);
    }

    public File dest;
    public String[] classpath = {"."};
    public boolean keepgoing = false;
    public boolean expandProperties = false;
    public boolean overwrite = false;
    public boolean quiet = false;
    public boolean verbose = false;
    public boolean all = false;
    public int verifySpecifierCount = -1;

    public void run(String... av) throws IOException {
        List<String> avl = new ArrayList<>(Arrays.asList(av));
        parseOptions(avl);
        if (avl.isEmpty())
            throw new IllegalArgumentException("Usage: indify [--dest dir] [option...] file...");
        if ("--java".equals(avl.get(0))) {
            avl.remove(0);
            try {
                runApplication(avl.toArray(new String[0]));
            } catch (Exception ex) {
                if (ex instanceof RuntimeException)  throw (RuntimeException) ex;
                throw new RuntimeException(ex);
            }
            return;
        }
        Exception err = null;
        for (String a : avl) {
            try {
                indify(a);
            } catch (Exception ex) {
                if (err == null)  err = ex;
                System.err.println("failure on "+a);
                if (!keepgoing)  break;
            }
        }
        if (err != null) {
            if (err instanceof IOException)  throw (IOException) err;
            throw (RuntimeException) err;
        }
    }

    /** Execute the given application under a class loader which indifies all application classes. */
    public void runApplication(String... av) throws Exception {
        List<String> avl = new ArrayList<>(Arrays.asList(av));
        String mainClassName = avl.remove(0);
        av = avl.toArray(new String[0]);
        Class<?> mainClass = Class.forName(mainClassName, true, makeClassLoader());
        java.lang.reflect.Method main = mainClass.getMethod("main", String[].class);
        try { main.setAccessible(true); } catch (SecurityException ex) { }
        main.invoke(null, (Object) av);
    }

    public void parseOptions(List<String> av) throws IOException {
        for (; !av.isEmpty(); av.remove(0)) {
            String a = av.get(0);
            if (a.startsWith("-")) {
                String a2 = null;
                int eq = a.indexOf('=');
                if (eq > 0) {
                    a2 = maybeExpandProperties(a.substring(eq+1));
                    a = a.substring(0, eq+1);
                }
                switch (a) {
                case "--java":
                    return;  // keep this argument
                case "-d": case "--dest": case "-d=": case "--dest=":
                    dest = new File(a2 != null ? a2 : maybeExpandProperties(av.remove(1)));
                    break;
                case "-cp": case "--classpath":
                    classpath = maybeExpandProperties(av.remove(1)).split("["+File.pathSeparatorChar+"]");
                    break;
                case "-k": case "--keepgoing": case "--keepgoing=":
                    keepgoing = booleanOption(a2);  // print errors but keep going
                    break;
                case "--expand-properties": case "--expand-properties=":
                    expandProperties = booleanOption(a2);  // expand property references in subsequent arguments
                    break;
                case "--verify-specifier-count": case "--verify-specifier-count=":
                    verifySpecifierCount = Integer.valueOf(a2);
                    break;
                case "--overwrite": case "--overwrite=":
                    overwrite = booleanOption(a2);  // overwrite output files
                    break;
                case "--all": case "--all=":
                    all = booleanOption(a2);  // copy all classes, even if no patterns
                    break;
                case "-q": case "--quiet": case "--quiet=":
                    quiet = booleanOption(a2);  // less output
                    break;
                case "-v": case "--verbose": case "--verbose=":
                    verbose = booleanOption(a2);  // more output
                    break;
                default:
                    throw new IllegalArgumentException("unrecognized flag: "+a);
                }
                continue;
            } else {
                break;
            }
        }
        if (dest == null && !overwrite)
            throw new RuntimeException("no output specified; need --dest d or --overwrite");
        if (expandProperties) {
            for (int i = 0; i < av.size(); i++)
                av.set(i, maybeExpandProperties(av.get(i)));
        }
    }

    private boolean booleanOption(String s) {
        if (s == null)  return true;
        switch (s) {
        case "true":  case "yes": case "on":  case "1": return true;
        case "false": case "no":  case "off": case "0": return false;
        }
        throw new IllegalArgumentException("unrecognized boolean flag="+s);
    }

    private String maybeExpandProperties(String s) {
        if (!expandProperties)  return s;
        Set<String> propsDone = new HashSet<>();
        while (s.contains("${")) {
            int lbrk = s.indexOf("${");
            int rbrk = s.indexOf('}', lbrk);
            if (rbrk < 0)  break;
            String prop = s.substring(lbrk+2, rbrk);
            if (!propsDone.add(prop))  break;
            String value = System.getProperty(prop);
            if (verbose)  System.err.println("expanding ${"+prop+"} => "+value);
            if (value == null)  break;
            s = s.substring(0, lbrk) + value + s.substring(rbrk+1);
        }
        return s;
    }

    public void indify(String a) throws IOException {
        File f = new File(a);
        String fn = f.getName();
        if (fn.endsWith(".class") && f.isFile())
            indifyFile(f, dest);
        else if (fn.endsWith(".jar") && f.isFile())
            indifyJar(f, dest);
        else if (f.isDirectory())
            indifyTree(f, dest);
        else if (!keepgoing)
            throw new RuntimeException("unrecognized file: "+a);
    }

    private void ensureDirectory(File dir) {
        if (dir.mkdirs() && !quiet)
            System.err.println("created "+dir);
    }

    public void indifyFile(File f, File dest) throws IOException {
        if (verbose)  System.err.println("reading "+f);
        Bytecode bytecode = new Bytecode(f); //creating new bytecode instance to trigger the api to read the class file for debugging
        ClassFile cf = new ClassFile(f);
        Logic logic = new Logic(cf, bytecode);
        boolean changed = logic.transform();
        logic.reportPatternMethods(quiet, keepgoing);
        if (changed || all) {
            File outfile;
            if (dest != null) {
                ensureDirectory(dest);
                outfile = classPathFile(dest, cf.nameString());
            } else {
                outfile = f;  // overwrite input file, no matter where it is
            }
            cf.writeTo(outfile);
            if (!quiet)  System.err.println("wrote "+outfile);
        }
    }

    File classPathFile(File pathDir, String className) {
        String qualname = className.replace('.','/')+".class";
        qualname = qualname.replace('/', File.separatorChar);
        return new File(pathDir, qualname);
    }

    public void indifyJar(File f, Object dest) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void indifyTree(File f, File dest) throws IOException {
        if (verbose)  System.err.println("reading directory: "+f);
        for (File f2 : f.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".class"))  return true;
                    if (name.contains("."))  return false;
                    // return true if it might be a package name:
                    return Character.isJavaIdentifierStart(name.charAt(0));
                }})) {
            if (f2.getName().endsWith(".class"))
                indifyFile(f2, dest);
            else if (f2.isDirectory())
                indifyTree(f2, dest);
        }
    }

    public ClassLoader makeClassLoader() {
        return new Loader();
    }
    private class Loader extends ClassLoader {
        Loader() {
            this(Indify.class.getClassLoader());
        }
        Loader(ClassLoader parent) {
            super(parent);
        }
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            File f = findClassInPath(name);
            if (f != null) {
                try {
                    Class<?> c = transformAndLoadClass(f);
                    if (c != null) {
                        if (resolve)  resolveClass(c);
                        return c;
                    }
                } catch (ClassNotFoundException ex) {
                    // fall through
                } catch (IOException ex) {
                    // fall through
                } catch (Exception ex) {
                    // pass error from reportPatternMethods, etc.
                    if (ex instanceof RuntimeException)  throw (RuntimeException) ex;
                    throw new RuntimeException(ex);
                }
            }
            return super.loadClass(name, resolve);
        }
        private File findClassInPath(String name) {
            for (String s : classpath) {
                File f = classPathFile(new File(s), name);
                //System.out.println("Checking for "+f);
                if (f.exists() && f.canRead()) {
                    return f;
                }
            }
            return null;
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                File f = findClassInPath(name);
                if (f != null) {
                    Class<?> c = transformAndLoadClass(f);
                    if (c != null)  return c;
                }
            } catch (IOException ex) {
                throw new ClassNotFoundException("IO error", ex);
            }
            throw new ClassNotFoundException();
        }
        private Class<?> transformAndLoadClass(File f) throws ClassNotFoundException, IOException {
            if (verbose)  System.err.println("Loading class from "+f);
            ClassFile cf = new ClassFile(f);
            Logic logic = new Logic(cf, new Bytecode(f));
            boolean changed = logic.transform();
            if (verbose && !changed)  System.err.println("(no change)");
            logic.reportPatternMethods(!verbose, keepgoing);
            byte[] bytes = cf.toByteArray();
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    private class Logic {
        // Indify logic, per se.
        ClassFile cf;
        Bytecode bytecode; //this is temporary for debugging
        final char[] poolMarks;
        final Map<Method,Constant> constants = new HashMap<>();
        final Map<Method,String> indySignatures = new HashMap<>();
        Logic(ClassFile cf, Bytecode bytecode) {
            this.cf = cf;
            this.bytecode = bytecode;
            poolMarks = new char[bytecode.classModel.constantPool().size()];
        }
        boolean transform() {
            if (!initializeMarks())  return false;
            if (!findPatternMethods())  return false;
            Pool pool = cf.pool;
            //for (Constant c : cp)  System.out.println("  # "+c);
            for (Method m : cf.methods) {
                if (constants.containsKey(m))  continue;  // don't bother
                // Transform references.
                int blab = 0;
                for (Instruction i = m.instructions(); i != null; i = i.next()) {
                    if (i.bc != INVOKESTATIC)  continue;
                    int methi = i.u2At(1);
                    if (poolMarks[methi] == 0)  continue;
                    Short[] ref = pool.getMemberRef((short)methi);
                    Method conm = findMember(cf.methods, ref[1], ref[2]);
                    if (conm == null)  continue;
                    Constant con = constants.get(conm);
                    if (con == null)  continue;
                    if (blab++ == 0 && !quiet)
                        System.err.println("patching "+cf.nameString()+"."+m);
                    //if (blab == 1) { for (Instruction j = m.instructions(); j != null; j = j.next()) System.out.println("  |"+j); }
                    if (con.tag == TAG_INVOKEDYNAMIC) {
                        // need to patch the following instruction too,
                        // but there are usually intervening argument pushes too
                        Instruction i2 = findPop(i);
                        Short[] ref2 = null;
                        short ref2i = 0;
                        if (i2 != null && i2.bc == INVOKEVIRTUAL &&
                                poolMarks[(char)(ref2i = (short) i2.u2At(1))] == 'D')
                            ref2 = pool.getMemberRef(ref2i);
                        if (ref2 == null || !"invokeExact".equals(pool.getString(ref2[1]))) {
                            System.err.println(m+": failed to create invokedynamic at "+i.pc);
                            continue;
                        }
                        String invType = pool.getString(ref2[2]);
                        String bsmType = indySignatures.get(conm);
                        if (!invType.equals(bsmType)) {
                            System.err.println(m+": warning: "+conm+" call type and local invoke type differ: "
                                    +bsmType+", "+invType);
                        }
                        assert(i.len == 3 || i2.len == 3);
                        if (!quiet)  System.err.println(i+" "+conm+";...; "+i2+" => invokedynamic "+con);
                        int start = i.pc + 3, end = i2.pc;
                        System.arraycopy(i.codeBase, start, i.codeBase, i.pc, end-start);
                        i.forceNext(0);  // force revisit of new instruction
                        i2.u1AtPut(-3, INVOKEDYNAMIC);
                        i2.u2AtPut(-2, con.index);
                        i2.u2AtPut(0, (short)0);
                        i2.u1AtPut(2, NOP);
                        //System.out.println(new Instruction(i.codeBase, i2.pc-3));
                    } else {
                        if (!quiet)  System.err.println(i+" "+conm+" => ldc "+con);
                        assert(i.len == 3);
                        i.u1AtPut(0, LDC_W);
                        i.u2AtPut(1, con.index);
                    }
                }
                //if (blab >= 1) { for (Instruction j = m.instructions(); j != null; j = j.next()) System.out.println("    |"+j); }
            }
            cf.methods.removeAll(constants.keySet());
            return true;
        }

        // Scan forward from the instruction to find where the stack p
        // below the current sp at the instruction.
        Instruction findPop(Instruction i) {
            //System.out.println("findPop from "+i);
            Pool pool = cf.pool;
            JVMState jvm = new JVMState();
        decode:
            for (i = i.clone().next(); i != null; i = i.next()) {
                String pops = INSTRUCTION_POPS[i.bc];
                //System.out.println("  "+i+" "+jvm.stack+" : "+pops.replace("$", " => "));
                if (pops == null)  break;
                if (jvm.stackMotion(i.bc))  continue decode;
                if (pops.indexOf('Q') >= 0) {
                    Short[] ref = pool.getMemberRef((short) i.u2At(1));
                    String type = simplifyType(pool.getString((byte) TAG_UTF8, ref[2]));
                    switch (i.bc) {
                    case GETSTATIC:
                    case GETFIELD:
                    case PUTSTATIC:
                    case PUTFIELD:
                        pops = pops.replace("Q", type);
                        break;
                    default:
                        if (!type.startsWith("("))
                            throw new InternalError(i.toString());
                        pops = pops.replace("Q$Q", type.substring(1).replace(")","$"));
                        break;
                    }
                    //System.out.println("special type: "+type+" => "+pops);
                }
                int npops = pops.indexOf('$');
                if (npops < 0)  throw new InternalError();
                if (npops > jvm.sp())  return i;
                List<Object> args = jvm.args(npops);
                int k = 0;
                for (Object x : args) {
                    char have = (Character) x;
                    char want = pops.charAt(k++);
                    if (have == 'X' || want == 'X')  continue;
                    if (have != want)  break decode;
                }
                if (pops.charAt(k++) != '$')  break decode;
                args.clear();
                while (k < pops.length())
                    args.add(pops.charAt(k++));
            }
            System.err.println("*** bailout on jvm: "+jvm.stack+" "+i);
            return null;
        }

        boolean findPatternMethods() {
            boolean found = false;
            for (char mark : "THI".toCharArray()) {
                for (Method m : cf.methods) {
                    if (!Modifier.isPrivate(m.access))  continue;
                    if (!Modifier.isStatic(m.access))  continue;
                    if (nameAndTypeMark(m.name, m.type) == mark) {
                        Constant con = scanPattern(m, mark);
                        if (con == null)  continue;
                        constants.put(m, con);
                        found = true;
                    }
                }
            }
            return found;
        }

        void reportPatternMethods(boolean quietly, boolean allowMatchFailure) {
            if (!quietly && !constants.keySet().isEmpty())
                System.err.println("pattern methods removed: "+constants.keySet());
            for (Method m : cf.methods) {
                if (nameMark(cf.pool.getString(m.name)) != 0 &&
                    constants.get(m) == null) {
                    String failure = "method has special name but fails to match pattern: "+m;
                    if (!allowMatchFailure)
                        throw new IllegalArgumentException(failure);
                    else if (!quietly)
                        System.err.println("warning: "+failure);
                }
            }
            if (verifySpecifierCount >= 0) {
                List<Object[]> specs = bootstrapMethodSpecifiers(false);
                int specsLen = (specs == null ? 0 : specs.size());
                // Pass by specsLen == 0, to help with associated (inner) classes.
                if (specsLen == 0)  specsLen = verifySpecifierCount;
                if (specsLen != verifySpecifierCount) {
                    throw new IllegalArgumentException("BootstrapMethods length is "+specsLen+" but should be "+verifySpecifierCount);
                }
            }
            if (!quiet)  System.err.flush();
        }

        /**
         * Initializes the marks for the constant pool entries.
         * <p>
         * This method iterates through the constant pool and assigns marks to each entry
         * based on its type and value. These marks are used to identify specific types of
         * constant pool entries .
         * <p>
         * The method iterates until no changes are made to the pool marks array in a complete pass.
         * This ensures that all dependent entries are processed correctly.
         *
         * @return true if any marks were changed, false otherwise.
         */
        boolean initializeMarks() {
            boolean changed = false;
            for (;;) {
                boolean changed1 = false;
                for (PoolEntry poolEntry : bytecode.classModel.constantPool()) {
                    // Get the index directly from PoolEntry
                    if (poolEntry == null) {
                        continue; // Skip null entries
                    }
                    int cpIndex = poolEntry.index();

                    char mark = poolMarks[cpIndex];
                    if (mark != 0) {
                        continue;
                    }

                    switch (poolEntry.tag()) {
                        case TAG_UTF8:
                            mark = nameMark(((Utf8Entry) poolEntry).stringValue());
                            break;
                        case TAG_NAMEANDTYPE:
                            NameAndTypeEntry nameAndTypeEntry = (NameAndTypeEntry) poolEntry;
                            int ref1 = nameAndTypeEntry.name().index();
                            int ref2 = nameAndTypeEntry.type().index();
                            mark = nameAndType_Mark(ref1, ref2);
                            break;
                        case TAG_CLASS: {
                            int n1 = ((ClassEntry) poolEntry).name().index();
                            char nmark = poolMarks[n1];
                            if ("DJ".indexOf(nmark) >= 0) {
                                mark = nmark;
                            }
                            break;
                        }
                        case TAG_FIELDREF:
                        case TAG_METHODREF: {
                            MemberRefEntry memberRefEntry = (MemberRefEntry) poolEntry;
                            int cl = memberRefEntry.owner().index();
                            int nt = memberRefEntry.nameAndType().index();
                            char classMark = poolMarks[cl];
                            if (classMark != 0) {
                                mark = classMark;  // java.lang.invoke.* or java.lang.* method
                                break;
                            }
                            String cls = (bytecode.classModel.constantPool().entryByIndex(cl) instanceof ClassEntry) ?
                                    ((ClassEntry) bytecode.classModel.constantPool().entryByIndex(cl)).name().stringValue() : "";
                            if (cls.equals(bytecode.thisClass.name().stringValue())) {
                                mark = switch (poolMarks[nt]) {
                                    case 'T', 'H', 'I' -> poolMarks[nt];
                                    default -> mark;
                                };
                            }
                            break;
                        }
                        default:
                            break;
                    }

                    if (mark != 0) {
                        poolMarks[cpIndex] = mark;
                        changed1 = true;
                    }
                }
                if (!changed1) {
                    break;
                }
                changed = true;
            }
            return changed;
        }
        char nameMark(String s) {
            if (s.startsWith("MT_"))                return 'T';
            else if (s.startsWith("MH_"))           return 'H';
            else if (s.startsWith("INDY_"))         return 'I';
            else if (s.startsWith("java/lang/invoke/"))  return 'D';
            else if (s.startsWith("java/lang/"))    return 'J';
            return 0;
        }
        char nameAndTypeMark(short n1, short n2) {
            char mark = poolMarks[(char)n1];
            if (mark == 0)  return 0;
            String descr = cf.pool.getString((byte) TAG_UTF8, n2);
            String requiredType;
            switch (poolMarks[(char)n1]) {
            case 'H': requiredType = "()Ljava/lang/invoke/MethodHandle;";  break;
            case 'T': requiredType = "()Ljava/lang/invoke/MethodType;";    break;
            case 'I': requiredType = "()Ljava/lang/invoke/MethodHandle;";  break;
            default:  return 0;
            }
            if (matchType(descr, requiredType))  return mark;
            return 0;
        }
        char nameAndType_Mark(int ref1, int ref2){
            char mark = poolMarks[ref1];
            if (mark == 0) return 0;
            String descriptor = (bytecode.classModel.constantPool().entryByIndex(ref2) instanceof Utf8Entry) ? ((Utf8Entry) bytecode.classModel.constantPool().entryByIndex(ref2)).stringValue() : "";
            String requiredType;
            switch (poolMarks[ref1]){
                case 'H', 'I': requiredType = "()Ljava/lang/invoke/MethodHandle;";  break;
                case 'T': requiredType = "()Ljava/lang/invoke/MethodType;";    break;
                default:  return 0;
            }
            if(matchType(descriptor, requiredType)) return mark;
            return 0;
        }

        boolean matchType(String descr, String requiredType) {
            if (descr.equals(requiredType))  return true;
            return false;
        }

        private class JVMState {
            final List<Object> stack = new ArrayList<>();
            int sp() { return stack.size(); }
            void push(Object x) { stack.add(x); }
            void push2(Object x) { stack.add(EMPTY_SLOT); stack.add(x); }
            void pushAt(int pos, Object x) { stack.add(stack.size()+pos, x); }
            Object pop() { return stack.remove(sp()-1); }
            Object top() { return stack.get(sp()-1); }
            List<Object> args(boolean hasRecv, String type) {
                return args(argsize(type) + (hasRecv ? 1 : 0));
            }
            List<Object> args(int argsize) {
                return stack.subList(sp()-argsize, sp());
            }
            boolean stackMotion(int bc) {
                switch (bc) {
                case POP:    pop();             break;
                case POP2:   pop(); pop();      break;
                case SWAP:   pushAt(-1, pop()); break;
                case DUP:    push(top());       break;
                case DUP_X1: pushAt(-2, top()); break;
                case DUP_X2: pushAt(-3, top()); break;
                // ? also: dup2{,_x1,_x2}
                default:  return false;
                }
                return true;
            }
        }
        private final String EMPTY_SLOT = "_";
        private void removeEmptyJVMSlots(List<Object> args) {
            for (;;) {
                int i = args.indexOf(EMPTY_SLOT);
                if (i >= 0 && i+1 < args.size()
                    && (isConstant(args.get(i+1), TAG_LONG) ||
                        isConstant(args.get(i+1), TAG_DOUBLE)))
                    args.remove(i);
                else  break;
            }
        }

        private Constant scanPattern(Method m, char patternMark) {
            if (verbose)  System.err.println("scan "+m+" for pattern="+patternMark);
            int wantTag;
            switch (patternMark) {
            case 'T': wantTag = TAG_METHODTYPE; break;
            case 'H': wantTag = TAG_METHODHANDLE; break;
            case 'I': wantTag = TAG_INVOKEDYNAMIC; break;
            default: throw new InternalError();
            }
            Instruction i = m.instructions();
            JVMState jvm = new JVMState();
            Pool pool = cf.pool;
            int branchCount = 0;
            Object arg;
            List<Object> args;
            List<Object> bsmArgs = null;  // args to invokeGeneric
        decode:
            for (; i != null; i = i.next()) {
                //System.out.println(jvm.stack+" "+i);
                int bc = i.bc;
                switch (bc) {
                case LDC:           jvm.push(pool.get(i.u1At(1)));   break;
                case LDC_W:         jvm.push(pool.get(i.u2At(1)));   break;
                case LDC2_W:        jvm.push2(pool.get(i.u2At(1)));  break;
                case ACONST_NULL:   jvm.push(null);                   break;
                case BIPUSH:        jvm.push((int)(byte) i.u1At(1)); break;
                case SIPUSH:        jvm.push((int)(short)i.u2At(1)); break;

                // these support creation of a restarg array
                case ANEWARRAY:
                    arg = jvm.pop();
                    if (!(arg instanceof Integer))  break decode;
                    arg = Arrays.asList(new Object[(Integer)arg]);
                    jvm.push(arg);
                    break;
                case DUP:
                    jvm.push(jvm.top()); break;
                case AASTORE:
                    args = jvm.args(3);  // array, index, value
                    if (args.get(0) instanceof List &&
                        args.get(1) instanceof Integer) {
                        @SuppressWarnings("unchecked")
                        List<Object> arg0 = (List<Object>)args.get(0);
                        arg0.set( (Integer)args.get(1), args.get(2) );
                    }
                    args.clear();
                    break;

                case NEW:
                {
                    String type = pool.getString((byte) TAG_CLASS, (short)i.u2At(1));
                    //System.out.println("new "+type);
                    switch (type) {
                    case "java/lang/StringBuilder":
                        jvm.push("StringBuilder");
                        continue decode;  // go to next instruction
                    }
                    break decode;  // bail out
                }

                case GETSTATIC:
                {
                    // int.class compiles to getstatic Integer.TYPE
                    int fieldi = i.u2At(1);
                    char mark = poolMarks[fieldi];
                    //System.err.println("getstatic "+fieldi+Arrays.asList(pool.getStrings(pool.getMemberRef((short)fieldi)))+mark);
                    if (mark == 'J') {
                        Short[] ref = pool.getMemberRef((short) fieldi);
                        String name = pool.getString((byte) TAG_UTF8, ref[1]);
                        if ("TYPE".equals(name)) {
                            String wrapperName = pool.getString((byte) TAG_CLASS, ref[0]).replace('/', '.');
                            // a primitive type descriptor
                            Class<?> primClass;
                            try {
                                primClass = (Class<?>) Class.forName(wrapperName).getField(name).get(null);
                            } catch (Exception ex) {
                                throw new InternalError("cannot load "+wrapperName+"."+name);
                            }
                            jvm.push(primClass);
                            break;
                        }
                    }
                    // unknown field; keep going...
                    jvm.push(UNKNOWN_CON);
                    break;
                }
                case PUTSTATIC:
                {
                    if (patternMark != 'I')  break decode;
                    jvm.pop();
                    // unknown field; keep going...
                    break;
                }

                case INVOKESTATIC:
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                {
                    boolean hasRecv = (bc != INVOKESTATIC);
                    int methi = i.u2At(1);
                    char mark = poolMarks[methi];
                    Short[] ref = pool.getMemberRef((short)methi);
                    String type = pool.getString((byte) TAG_UTF8, ref[2]);
                    //System.out.println("invoke "+pool.getString(TAG_UTF8, ref[1])+" "+Arrays.asList(ref)+" : "+type);
                    args = jvm.args(hasRecv, type);
                    String intrinsic = null;
                    Constant con;
                    if (mark == 'D' || mark == 'J') {
                        intrinsic = pool.getString((byte) TAG_UTF8, ref[1]);
                        if (mark == 'J') {
                            String cls = pool.getString((byte) TAG_CLASS, ref[0]);
                            cls = cls.substring(1+cls.lastIndexOf('/'));
                            intrinsic = cls+"."+intrinsic;
                        }
                        //System.out.println("recognized intrinsic "+intrinsic);
                        byte refKind = -1;
                        switch (intrinsic) {
                        case "findGetter":          refKind = (byte) GETTER.refKind;            break;
                        case "findStaticGetter":    refKind = (byte) STATIC_GETTER.refKind;     break;
                        case "findSetter":          refKind = (byte) SETTER.refKind;            break;
                        case "findStaticSetter":    refKind = (byte) STATIC_SETTER.refKind;     break;
                        case "findVirtual":         refKind = (byte) VIRTUAL.refKind;           break;
                        case "findStatic":          refKind = (byte) STATIC.refKind;            break;
                        case "findSpecial":         refKind = (byte) SPECIAL.refKind;           break;
                        case "findConstructor":     refKind = (byte) CONSTRUCTOR.refKind;       break;
                        }
                        if (refKind >= 0 && (con = parseMemberLookup(refKind, args)) != null) {
                            args.clear(); args.add(con);
                            continue;
                        }
                    }
                    Method ownMethod = null;
                    if (mark == 'T' || mark == 'H' || mark == 'I') {
                        ownMethod = findMember(cf.methods, ref[1], ref[2]);
                    }
                    //if (intrinsic != null)  System.out.println("intrinsic = "+intrinsic);
                    switch (intrinsic == null ? "" : intrinsic) {
                    case "fromMethodDescriptorString":
                        con = makeMethodTypeCon(args.get(0));
                        args.clear(); args.add(con);
                        continue;
                    case "methodType": {
                        flattenVarargs(args);  // there are several overloadings, some with varargs
                        StringBuilder buf = new StringBuilder();
                        String rtype = null;
                        for (Object typeArg : args) {
                            if (typeArg instanceof Class) {
                                Class<?> argClass = (Class<?>) typeArg;
                                if (argClass.isPrimitive()) {
                                    char tchar;
                                    switch (argClass.getName()) {
                                    case "void":    tchar = 'V'; break;
                                    case "boolean": tchar = 'Z'; break;
                                    case "byte":    tchar = 'B'; break;
                                    case "char":    tchar = 'C'; break;
                                    case "short":   tchar = 'S'; break;
                                    case "int":     tchar = 'I'; break;
                                    case "long":    tchar = 'J'; break;
                                    case "float":   tchar = 'F'; break;
                                    case "double":  tchar = 'D'; break;
                                    default:  throw new InternalError(argClass.toString());
                                    }
                                    buf.append(tchar);
                                } else {
                                    // should not happen, but...
                                    buf.append('L').append(argClass.getName().replace('.','/')).append(';');
                                }
                            } else if (typeArg instanceof Constant) {
                                Constant argCon = (Constant) typeArg;
                                if (argCon.tag == TAG_CLASS) {
                                    String cn = pool.get(argCon.itemIndex()).itemString();
                                    if (cn.endsWith(";"))
                                        buf.append(cn);
                                    else
                                        buf.append('L').append(cn).append(';');
                                } else {
                                    break decode;
                                }
                            } else {
                                break decode;
                            }
                            if (rtype == null) {
                                // first arg is treated differently
                                rtype = buf.toString();
                                buf.setLength(0);
                                buf.append('(');
                            }
                        }
                        buf.append(')').append(rtype);
                        con = con = makeMethodTypeCon(buf.toString());
                        args.clear(); args.add(con);
                        continue;
                    }
                    case "lookup":
                    case "dynamicInvoker":
                        args.clear(); args.add(intrinsic);
                        continue;
                    case "lookupClass":
                        if (args.equals(Arrays.asList("lookup"))) {
                            // fold lookup().lookupClass() to the enclosing class
                            args.clear(); args.add(pool.get(cf.thisc));
                            continue;
                        }
                        break;
                    case "invoke":
                    case "invokeGeneric":
                    case "invokeWithArguments":
                        if (patternMark != 'I')  break decode;
                        if ("invokeWithArguments".equals(intrinsic))
                            flattenVarargs(args);
                        bsmArgs = new ArrayList<>(args);
                        args.clear(); args.add("invokeGeneric");
                        continue;
                    case "Integer.valueOf":
                    case "Float.valueOf":
                    case "Long.valueOf":
                    case "Double.valueOf":
                        removeEmptyJVMSlots(args);
                        if (args.size() == 1) {
                            arg = args.remove(0);
                            assert(3456 == (TAG_INTEGER*1000 + TAG_FLOAT*100 + TAG_LONG*10 + TAG_DOUBLE));
                            if (isConstant(arg, TAG_INTEGER + "IFLD".indexOf(intrinsic.charAt(0)))
                                || arg instanceof Number) {
                                args.add(arg); continue;
                            }
                        }
                        break decode;
                    case "StringBuilder.append":
                        // allow calls like ("value = "+x)
                        removeEmptyJVMSlots(args);
                        args.subList(1, args.size()).clear();
                        continue;
                    case "StringBuilder.toString":
                        args.clear();
                        args.add(intrinsic);
                        continue;
                    }
                    if (!hasRecv && ownMethod != null && patternMark != 0) {
                        con = constants.get(ownMethod);
                        if (con == null)  break decode;
                        args.clear(); args.add(con);
                        continue;
                    } else if (type.endsWith(")V")) {
                        // allow calls like println("reached the pattern method")
                        args.clear();
                        continue;
                    }
                    break decode;  // bail out for most calls
                }
                case ARETURN:
                {
                    ++branchCount;
                    if (bsmArgs != null) {
                        // parse bsmArgs as (MH, lookup, String, MT, [extra])
                        Constant indyCon = makeInvokeDynamicCon(bsmArgs);
                        if (indyCon != null) {
                            Constant typeCon = (Constant) bsmArgs.get(3);
                            indySignatures.put(m, pool.getString(typeCon.itemIndex()));
                            return indyCon;
                        }
                        System.err.println(m+": inscrutable bsm arguments: "+bsmArgs);
                        break decode;  // bail out
                    }
                    arg = jvm.pop();
                    if (branchCount == 2 && UNKNOWN_CON.equals(arg))
                        break;  // merge to next path
                    if (isConstant(arg, wantTag))
                        return (Constant) arg;
                    break decode;  // bail out
                }
                default:
                    if (jvm.stackMotion(i.bc))  break;
                    if (bc >= ICONST_M1 && bc <= DCONST_1)
                        { jvm.push(INSTRUCTION_CONSTANTS[bc - ICONST_M1]); break; }
                    if (patternMark == 'I') {
                        // these support caching paths in INDY_x methods
                        if (bc == ALOAD || bc >= ALOAD_0 && bc <= ALOAD_3)
                            { jvm.push(UNKNOWN_CON); break; }
                        if (bc == ASTORE || bc >= ASTORE_0 && bc <= ASTORE_3)
                            { jvm.pop(); break; }
                        switch (bc) {
                        case GETFIELD:
                        case AALOAD:
                            jvm.push(UNKNOWN_CON); break;
                        case IFNULL:
                        case IFNONNULL:
                            // ignore branch target
                            if (++branchCount != 1)  break decode;
                            jvm.pop();
                            break;
                        case CHECKCAST:
                            arg = jvm.top();
                            if ("invokeWithArguments".equals(arg) ||
                                "invokeGeneric".equals(arg))
                                break;  // assume it is a helpful cast
                            break decode;
                        default:
                            break decode;  // bail out
                        }
                        continue decode; // go to next instruction
                    }
                    break decode;  // bail out
                } //end switch
            }
            System.err.println(m+": bailout on "+i+" jvm stack: "+jvm.stack);
            return null;
        }
        private final String UNKNOWN_CON = "<unknown>";

        private void flattenVarargs(List<Object> args) {
            int size = args.size();
            if (size > 0 && args.get(size - 1) instanceof List) {
                List<?> removedArg = (List<?>) args.remove(size - 1);
                args.addAll(removedArg);
            }
        }

        private boolean isConstant(Object x, int tag) {
            return x instanceof Constant && ((Constant)x).tag == tag;
        }
        private Constant makeMethodTypeCon(Object x) {
            short utfIndex;
            if (x instanceof String)
                utfIndex = (short) cf.pool.addConstant((byte) TAG_UTF8, x).index;
            else if (isConstant(x, TAG_STRING))
                utfIndex = ((Constant)x).itemIndex();
            else  return null;
            return cf.pool.addConstant((byte) TAG_METHODTYPE, utfIndex);
        }
        private Constant parseMemberLookup(byte refKind, List<Object> args) {
            // E.g.: lookup().findStatic(Foo.class, "name", MethodType)
            if (args.size() != 4)  return null;
            int argi = 0;
            if (!"lookup".equals(args.get(argi++)))  return null;
            short refindex, cindex, ntindex, nindex, tindex;
            Object con;
            if (!isConstant(con = args.get(argi++), TAG_CLASS))  return null;
            cindex = (short)((Constant)con).index;
            if (!isConstant(con = args.get(argi++), TAG_STRING))  return null;
            nindex = ((Constant)con).itemIndex();
            if (isConstant(con = args.get(argi++), TAG_METHODTYPE) ||
                isConstant(con, TAG_CLASS)) {
                tindex = ((Constant)con).itemIndex();
            } else return null;
            ntindex = (short) cf.pool.addConstant((byte) TAG_NAMEANDTYPE,
                    new Short[]{ nindex, tindex }).index;
            byte reftag = TAG_METHODREF;
            if (refKind <= (byte) STATIC_SETTER.refKind)
                reftag = TAG_FIELDREF;
            else if (refKind == (byte) INTERFACE_VIRTUAL.refKind)
                reftag = TAG_INTERFACEMETHODREF;
            Constant ref = cf.pool.addConstant(reftag, new Short[]{ cindex, ntindex });
            return cf.pool.addConstant((byte) TAG_METHODHANDLE, new Object[]{ refKind, (short)ref.index });
        }
        private Constant makeInvokeDynamicCon(List<Object> args) {
            // E.g.: MH_bsm.invokeGeneric(lookup(), "name", MethodType, "extraArg")
            removeEmptyJVMSlots(args);
            if (args.size() < 4)  return null;
            int argi = 0;
            short nindex, tindex, ntindex, bsmindex;
            Object con;
            if (!isConstant(con = args.get(argi++), TAG_METHODHANDLE))  return null;
            bsmindex = (short) ((Constant)con).index;
            if (!"lookup".equals(args.get(argi++)))  return null;
            if (!isConstant(con = args.get(argi++), TAG_STRING))  return null;
            nindex = ((Constant)con).itemIndex();
            if (!isConstant(con = args.get(argi++), TAG_METHODTYPE))  return null;
            tindex = ((Constant)con).itemIndex();
            ntindex = (short) cf.pool.addConstant((byte) TAG_NAMEANDTYPE,
                                                  new Short[]{ nindex, tindex }).index;
            List<Object> extraArgs = new ArrayList<Object>();
            if (argi < args.size()) {
                extraArgs.addAll(args.subList(argi, args.size() - 1));
                Object lastArg = args.get(args.size() - 1);
                if (lastArg instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> lastArgs = (List<Object>) lastArg;
                    removeEmptyJVMSlots(lastArgs);
                    extraArgs.addAll(lastArgs);
                } else {
                    extraArgs.add(lastArg);
                }
            }
            List<Short> extraArgIndexes = new CountedList<>(Short.class);
            for (Object x : extraArgs) {
                if (x instanceof Number) {
                    Object num = null; byte numTag = 0;
                    if (x instanceof Integer) { num = x; numTag = TAG_INTEGER; }
                    if (x instanceof Float)   { num = Float.floatToRawIntBits((Float)x); numTag = TAG_FLOAT; }
                    if (x instanceof Long)    { num = x; numTag = TAG_LONG; }
                    if (x instanceof Double)  { num = Double.doubleToRawLongBits((Double)x); numTag = TAG_DOUBLE; }
                    if (num != null)  x = cf.pool.addConstant(numTag, x);
                }
                if (!(x instanceof Constant)) {
                    System.err.println("warning: unrecognized BSM argument "+x);
                    return null;
                }
                extraArgIndexes.add((short) ((Constant)x).index);
            }
            List<Object[]> specs = bootstrapMethodSpecifiers(true);
            int specindex = -1;
            Object[] spec = new Object[]{ bsmindex, extraArgIndexes };
            for (Object[] spec1 : specs) {
                if (Arrays.equals(spec1, spec)) {
                    specindex = specs.indexOf(spec1);
                    if (verbose)  System.err.println("reusing BSM specifier: "+spec1[0]+spec1[1]);
                    break;
                }
            }
            if (specindex == -1) {
                specindex = (short) specs.size();
                specs.add(spec);
                if (verbose)  System.err.println("adding BSM specifier: "+spec[0]+spec[1]);
            }
            return cf.pool.addConstant((byte) TAG_INVOKEDYNAMIC,
                        new Short[]{ (short)specindex, ntindex });
        }

        List<Object[]> bootstrapMethodSpecifiers(boolean createIfNotFound) {
            Attr bsms = cf.findAttr("BootstrapMethods");
            if (bsms == null) {
                if (!createIfNotFound)  return null;
                bsms = new Attr(cf, "BootstrapMethods", new byte[]{0,0});
                assert(bsms == cf.findAttr("BootstrapMethods"));
            }
            if (bsms.item instanceof byte[]) {
                // unflatten
                List<Object[]> specs = new CountedList<>(Object[].class);
                DataInputStream in = new DataInputStream(new ByteArrayInputStream((byte[]) bsms.item));
                try {
                    int len = (char) in.readShort();
                    for (int i = 0; i < len; i++) {
                        short bsm = in.readShort();
                        int argc = (char) in.readShort();
                        List<Short> argv = new CountedList<>(Short.class);
                        for (int j = 0; j < argc; j++)
                            argv.add(in.readShort());
                        specs.add(new Object[]{ bsm, argv });
                    }
                } catch (IOException ex) { throw new InternalError(); }
                bsms.item = specs;
            }
            @SuppressWarnings("unchecked")
            List<Object[]> specs = (List<Object[]>) bsms.item;
            return specs;
        }
    }

    private DataInputStream openInput(File f) throws IOException {
        return new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
    }

    private byte[] openInputIntoBytes(File f) throws IOException{
        try{
            return Files.readAllBytes(f.toPath());
        }
        catch(IOException e){
            throw new IOException("Error reading file: "+f);
        }
    }

    private DataOutputStream openOutput(File f) throws IOException {
        if (!overwrite && f.exists())
            throw new IOException("file already exists: "+f);
        ensureDirectory(f.getParentFile());
        return new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
    }

    static byte[] readRawBytes(DataInputStream in, int size) throws IOException {
        byte[] bytes = new byte[size];
        int nr = in.read(bytes);
        if (nr != size)
            throw new InternalError("wrong size: "+nr);
        return bytes;
    }

    private interface Chunk {
        void readFrom(DataInputStream in) throws IOException;
        void writeTo(DataOutputStream out) throws IOException;
    }

    private static class CountedList<T> extends ArrayList<T> implements Chunk {
        final Class<? extends T> itemClass;
        final int rowlen;
        CountedList(Class<? extends T> itemClass, int rowlen) {
            this.itemClass = itemClass;
            this.rowlen = rowlen;
        }
        CountedList(Class<? extends T> itemClass) { this(itemClass, -1); }
        public void readFrom(DataInputStream in) throws IOException {
            int count = in.readUnsignedShort();
            while (size() < count) {
                if (rowlen < 0) {
                    add(readInput(in, itemClass));
                } else {
                    Class<?> elemClass = itemClass.getComponentType();
                    Object[] row = (Object[]) java.lang.reflect.Array.newInstance(elemClass, rowlen);
                    for (int i = 0; i < rowlen; i++)
                        row[i] = readInput(in, elemClass);
                    add(itemClass.cast(row));
                }
            }
        }
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeShort((short)size());
            for (T item : this) {
                writeOutput(out, item);
            }
        }
    }

    private static <T> T readInput(DataInputStream in, Class<T> dataClass) throws IOException {
        Object data;
        if (dataClass == Integer.class) {
            data = in.readInt();
        } else if (dataClass == Short.class) {
            data = in.readShort();
        } else if (dataClass == Byte.class) {
            data = in.readByte();
        } else if (dataClass == String.class) {
            data = in.readUTF();
        } else if (Chunk.class.isAssignableFrom(dataClass)) {
            T obj;
            try { obj = dataClass.getDeclaredConstructor().newInstance(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            ((Chunk)obj).readFrom(in);
            data = obj;
        } else {
            throw new InternalError("bad input datum: "+dataClass);
        }
        return dataClass.cast(data);
    }
    private static <T> T readInput(byte[] bytes, Class<T> dataClass) {
        try {
            return readInput(new DataInputStream(new ByteArrayInputStream(bytes)), dataClass);
        } catch (IOException ex) {
            throw new InternalError();
        }
    }
    private static void readInputs(DataInputStream in, Object... data) throws IOException {
        for (Object x : data)  ((Chunk)x).readFrom(in);
    }

    private static void writeOutput(DataOutputStream out, Object data) throws IOException {
        if (data == null) {
            return;
        } if (data instanceof Integer) {
            out.writeInt((Integer)data);
        } else if (data instanceof Long) {
            out.writeLong((Long)data);
        } else if (data instanceof Short) {
            out.writeShort((Short)data);
        } else if (data instanceof Byte) {
            out.writeByte((Byte)data);
        } else if (data instanceof String) {
            out.writeUTF((String)data);
        } else if (data instanceof byte[]) {
            out.write((byte[])data);
        } else if (data instanceof Object[]) {
            for (Object x : (Object[]) data)
                writeOutput(out, x);
        } else if (data instanceof Chunk) {
            Chunk x = (Chunk) data;
            x.writeTo(out);
        } else if (data instanceof List) {
            for (Object x : (List<?>) data)
                writeOutput(out, x);
        } else {
            throw new InternalError("bad output datum: "+data+" : "+data.getClass().getName());
        }
    }
    private static void writeOutputs(DataOutputStream out, Object... data) throws IOException {
        for (Object x : data)  writeOutput(out, x);
    }

    public class Bytecode {
        Bytecode(File f) throws IOException {
            byte[] bytes = openInputIntoBytes(f);
            try{
                parseFrom(bytes);
            } catch (Exception e){
                throw new IOException("Error parsing file: "+f, e);
            }
        }
        public ClassModel classModel;
        public int magicNumber, classFileVersion, accessFlags;
        public ClassEntry thisClass, superClass;
        public final List<MethodModel>  methods = new ArrayList<>();
        public final List<FieldModel>   fields = new ArrayList<>();
        public final List<Attribute<?>> attributes = new ArrayList<>();
        public final List<ClassEntry>   interfaces = new ArrayList<>();
        public final List<PoolEntry>    pool = new ArrayList<>();

        public void parseFrom(byte[] bytes) throws IOException {
            ClassHierarchyResolver classHierarchyResolver = classDesc -> {
                // Treat all classes as interfaces
                return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
            };

            try {
                List<VerifyError> errors = java.lang.classfile.ClassFile.of(ClassHierarchyResolverOption.of(classHierarchyResolver)).verify(bytes);
                if (!errors.isEmpty()) {
                    for (VerifyError e : errors) {
                        System.err.println(e.getMessage());
                    }
                    throw new IOException("Verification failed");
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }

            classModel = java.lang.classfile.ClassFile.of().parse(bytes);

            pool.addFirst(null);
            for (PoolEntry poolEntry : classModel.constantPool()) {
                this.pool.add(poolEntry);
            }


            magicNumber = MAGIC_NUMBER;
            classFileVersion = classModel.majorVersion();
            accessFlags = classModel.flags().flagsMask();

            thisClass = classModel.thisClass();
            superClass = (classModel.superclass().isPresent() ? classModel.superclass().get() : null);

            methods.addAll(classModel.methods());
            fields.addAll(classModel.fields());
            attributes.addAll(classModel.attributes());

            interfaces.addAll(classModel.interfaces());
        }
    }

    public abstract static class Outer {
        public abstract List<? extends Inner> inners();
        protected void linkInners() {
            for (Inner i : inners()) {
                i.linkOuter(this);
                if (i instanceof Outer)
                    ((Outer)i).linkInners();
            }
        }
        public <T extends Outer> T outer(Class<T> c) {
            for (Outer walk = this;; walk = ((Inner)walk).outer()) {
                if (c.isInstance(walk))
                    return c.cast(walk);
                //if (!(walk instanceof Inner))  return null;
            }
        }

        public abstract List<Attr> attrs();
        public Attr findAttr(String name) {
            return findAttr(outer(ClassFile.class).pool.stringIndex(name, false));
        }
        public Attr findAttr(int name) {
            if (name == 0)  return null;
            for (Attr a : attrs()) {
                if (a.name == name)  return a;
            }
            return null;
        }
    }
    public interface Inner { Outer outer(); void linkOuter(Outer o); }
    public abstract static class InnerOuter extends Outer implements Inner {
        public Outer outer;
        public Outer outer() { return outer; }
        public void linkOuter(Outer o) { assert(outer == null); outer = o; }
    }
    public static class Constant<T> implements Chunk {
        public final byte tag;
        public final T item;
        public final int index;
        public Constant(int index, byte tag, T item) {
            this.index = index;
            this.tag = tag;
            this.item = item;
        }
        public Constant checkTag(byte tag) {
            if (this.tag != tag)  throw new InternalError(this.toString());
            return this;
        }
        public String itemString() { return (String)item; }
        public Short itemIndex() { return (Short)item; }
        public Short[] itemIndexes() { return (Short[])item; }
        public void readFrom(DataInputStream in) throws IOException {
            throw new InternalError("do not call");
        }
        public void writeTo(DataOutputStream out) throws IOException {
            writeOutputs(out, tag, item);
        }
        public boolean equals(Object x) { return (x instanceof Constant && equals((Constant)x)); }
        public boolean equals(Constant that) {
            return (this.tag == that.tag && this.itemAsComparable().equals(that.itemAsComparable()));
        }
        public int hashCode() { return (tag * 31) + this.itemAsComparable().hashCode(); }
        public Object itemAsComparable() {
            switch (tag) {
            case TAG_DOUBLE:   return Double.longBitsToDouble((Long)item);
            case TAG_FLOAT:    return Float.intBitsToFloat((Integer)item);
            }
            return (item instanceof Object[] ? Arrays.asList((Object[])item) : item);
        }
        public String toString() {
            String itstr = String.valueOf(itemAsComparable());
            return (index + ":" + tagName(tag) + (itstr.startsWith("[")?"":"=") + itstr);
        }
        private static String[] TAG_NAMES;
        public static String tagName(byte tag) {  // used for error messages
            if (TAG_NAMES == null)
                TAG_NAMES = ("None Utf8 Unicode Integer Float Long Double Class String"
                             +" Fieldref Methodref InterfaceMethodref NameAndType #13 #14"
                             +" MethodHandle MethodType InvokeDynamic#17 InvokeDynamic").split(" ");
            if ((tag & 0xFF) >= TAG_NAMES.length)  return "#"+(tag & 0xFF);
            return TAG_NAMES[tag & 0xFF];
        }
    }

    public static class Pool extends CountedList<Constant> implements Chunk {
        private Map<String,Short> strings = new TreeMap<>();

        public Pool() {
            super(Constant.class);
        }
        public void readFrom(DataInputStream in) throws IOException {
            int count = in.readUnsignedShort();
            add(null);  // always ignore first item
            while (size() < count) {
                readConstant(in);
            }
        }
        public <T> Constant addConstant(byte tag, T item) {
            Constant<T> con = new Constant<>(size(), tag, item);
            int idx = indexOf(con);
            if (idx >= 0)  return get(idx);
            add(con);
            if (tag == TAG_UTF8)  strings.put((String)item, (short) con.index);
            return con;
        }
        private void readConstant(DataInputStream in) throws IOException {
            byte tag = in.readByte();
            int index = size();
            Object arg;
            switch (tag) {
            case TAG_UTF8:
                arg = in.readUTF();
                strings.put((String) arg, (short) size());
                break;
            case TAG_INTEGER:
            case TAG_FLOAT:
                arg = in.readInt(); break;
            case TAG_LONG:
            case TAG_DOUBLE:
                add(new Constant<>(index, tag, in.readLong()));
                add(null);
                return;
            case TAG_CLASS:
            case TAG_STRING:
                arg = in.readShort(); break;
            case TAG_FIELDREF:
            case TAG_METHODREF:
            case TAG_INTERFACEMETHODREF:
            case TAG_NAMEANDTYPE:
            case TAG_INVOKEDYNAMIC:
                // read an ordered pair
                arg = new Short[] { in.readShort(), in.readShort() };
                break;
            case TAG_METHODHANDLE:
                // read an ordered pair; first part is a u1 (not u2)
                arg = new Object[] { in.readByte(), in.readShort() };
                break;
            case TAG_METHODTYPE:
                arg = in.readShort(); break;
            default:
                throw new InternalError("bad CP tag "+tag);
            }
            add(new Constant<>(index, tag, arg));
        }

        // Access:
        public Constant get(int index) {
            // extra 1-bits get into the shorts
            return super.get((char) index);
        }
        String getString(byte tag, short index) {
            get(index).checkTag(tag);
            return getString(index);
        }
        String getString(short index) {
            Object v = get(index).item;
            if (v instanceof Short)
                v = get((Short)v).checkTag((byte) TAG_UTF8).item;
            return (String) v;
        }
        String[] getStrings(Short[] indexes) {
            String[] res = new String[indexes.length];
            for (int i = 0; i < indexes.length; i++)
                res[i] = getString(indexes[i]);
            return res;
        }
        int stringIndex(String name, boolean createIfNotFound) {
            Short x = strings.get(name);
            if (x != null)  return (char)(int) x;
            if (!createIfNotFound)  return 0;
            return addConstant((byte) TAG_UTF8, name).index;
        }
        Short[] getMemberRef(short index) {
            Short[] cls_nnt = get(index).itemIndexes();
            Short[] name_type = get(cls_nnt[1]).itemIndexes();
            return new Short[]{ cls_nnt[0], name_type[0], name_type[1] };
        }
    }

    public class ClassFile extends Outer implements Chunk {
        ClassFile(File f) throws IOException {
            DataInputStream in = openInput(f);
            try {
                readFrom(in);
            } finally {
                if (in != null)  in.close();
            }
        }

        public int                magic, version;  // <min:maj>
        public final Pool         pool       = new Pool();
        public short              access, thisc, superc;
        public final List<Short>  interfaces = new CountedList<>(Short.class);
        public final List<Field>  fields     = new CountedList<>(Field.class);
        public final List<Method> methods    = new CountedList<>(Method.class);
        public final List<Attr>   attrs      = new CountedList<>(Attr.class);

        public final void readFrom(DataInputStream in) throws IOException {
            magic = in.readInt(); version = in.readInt();
            if (magic != 0xCAFEBABE)  throw new IOException("bad magic number");
            pool.readFrom(in);
            Code_index = pool.stringIndex("Code", false);
            access = in.readShort(); thisc = in.readShort(); superc = in.readShort();
            readInputs(in, interfaces, fields, methods, attrs);
            if (in.read() >= 0)  throw new IOException("junk after end of file");
            linkInners();
        }

        void writeTo(File f) throws IOException {
            DataOutputStream out = openOutput(f);
            try {
                writeTo(out);
            } finally {
                out.close();
            }
        }

        public void writeTo(DataOutputStream out) throws IOException {
            writeOutputs(out, magic, version, pool,
                         access, thisc, superc, interfaces,
                         fields, methods, attrs);
        }

        public byte[] toByteArray() {
            try {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                writeTo(new DataOutputStream(buf));
                return buf.toByteArray();
            } catch (IOException ex) {
                throw new InternalError();
            }
        }

        public List<Inner> inners() {
            List<Inner> inns = new ArrayList<>();
            inns.addAll(fields); inns.addAll(methods); inns.addAll(attrs);
            return inns;
        }
        public List<Attr> attrs() { return attrs; }

        // derived stuff:
        public String nameString() { return pool.getString((byte) TAG_CLASS, thisc); }
        int Code_index;
    }

    private static <T extends Member> T findMember(List<T> mems, int name, int type) {
        if (name == 0 || type == 0)  return null;
        for (T m : mems) {
            if (m.name == name && m.type == type)  return m;
        }
        return null;
    }

    public static class Member extends InnerOuter implements Chunk {
        public short access, name, type;
        public final List<Attr> attrs = new CountedList<>(Attr.class);
        public void readFrom(DataInputStream in) throws IOException {
            access = in.readShort(); name = in.readShort(); type = in.readShort();
            readInputs(in, attrs);
        }
        public void writeTo(DataOutputStream out) throws IOException {
            writeOutputs(out, access, name, type, attrs);
        }
        public List<Attr> inners() { return attrs; }
        public List<Attr> attrs() { return attrs; }
        public ClassFile outer() { return (ClassFile) outer; }
        public String nameString() { return outer().pool.getString((byte) TAG_UTF8, name); }
        public String typeString() { return outer().pool.getString((byte) TAG_UTF8, type); }
        public String toString() {
            if (outer == null)  return super.toString();
            return nameString() + (this instanceof Method ? "" : ":")
                    + simplifyType(typeString());
        }
    }
    public static class Field extends Member {
    }
    public static class Method extends Member {
        public Code code() {
            Attr a = findAttr("Code");
            if (a == null)  return null;
            return (Code) a.item;
        }
        public Instruction instructions() {
            Code code = code();
            if (code == null)  return null;
            return code.instructions();
        }
    }

    public static class Attr extends InnerOuter implements Chunk {
        public short name;
        public int size = -1;  // no pre-declared size
        public Object item;

        public Attr() {}
        public Attr(Outer outer, String name, Object item) {
            ClassFile cf = outer.outer(ClassFile.class);
            linkOuter(outer);
            this.name = (short) cf.pool.stringIndex(name, true);
            this.item = item;
            outer.attrs().add(this);
        }
        public void readFrom(DataInputStream in) throws IOException {
            name = in.readShort();
            size = in.readInt();
            item = readRawBytes(in, size);
        }
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeShort(name);
            // write the 4-byte size header and then the contents:
            byte[] bytes;
            int trueSize;
            if (item instanceof byte[]) {
                bytes = (byte[]) item;
                out.writeInt(trueSize = bytes.length);
                out.write(bytes);
            } else {
                trueSize = flatten(out);
                //if (!(item instanceof Code))  System.err.println("wrote complex attr name="+(int)(char)name+" size="+trueSize+" data="+Arrays.toString(flatten()));
            }
            if (trueSize != size && size >= 0)
                System.err.println("warning: attribute size changed "+size+" to "+trueSize);
        }
        public void linkOuter(Outer o) {
            super.linkOuter(o);
            if (item instanceof byte[] &&
                outer instanceof Method &&
                ((Method)outer).outer().Code_index == name) {
                    item = readInput((byte[])item, Code.class);
            }
        }
        public List<Inner> inners() {
            if (item instanceof Inner)
                return Collections.nCopies(1, (Inner)item);
            return Collections.emptyList();
        }
        public List<Attr> attrs() { return null; }  // Code overrides this
        public byte[] flatten() {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.max(20, size));
            flatten(buf);
            return buf.toByteArray();
        }
        public int flatten(DataOutputStream out) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.max(20, size));
            int trueSize = flatten(buf);
            out.writeInt(trueSize);
            buf.writeTo(out);
            return trueSize;
        }
        private int flatten(ByteArrayOutputStream buf) {
            try {
                writeOutput(new DataOutputStream(buf), item);
                return buf.size();
            } catch (IOException ex) {
                throw new InternalError();
            }
        }
        public String nameString() {
            ClassFile cf = outer(ClassFile.class);
            if (cf == null)  return "#"+name;
            return cf.pool.getString(name);
        }
        public String toString() {
            return nameString()+(size < 0 ? "=" : "["+size+"]=")+item;
        }
    }

    public static class Code extends InnerOuter implements Chunk {
        public short stacks, locals;
        public byte[] bytes;
        public final List<Short[]> etable = new CountedList<>(Short[].class, 4);
        public final List<Attr> attrs = new CountedList<>(Attr.class);
        // etable[N] = (N)*{ startpc, endpc, handlerpc, catchtype }
        public void readFrom(DataInputStream in) throws IOException {
            stacks = in.readShort(); locals = in.readShort();
            bytes = readRawBytes(in, in.readInt());
            readInputs(in, etable, attrs);
        }
        public void writeTo(DataOutputStream out) throws IOException {
            writeOutputs(out, stacks, locals, bytes.length, bytes, etable, attrs);
        }
        public List<Attr> inners() { return attrs; }
        public List<Attr> attrs() { return attrs; }
        public Instruction instructions() {
            return new Instruction(bytes, 0);
        }
    }

    private static final Object[] INSTRUCTION_CONSTANTS = {
        -1, 0, 1, 2, 3, 4, 5, 0L, 1L, 0.0F, 1.0F, 2.0F, 0.0D, 1.0D
    };

    private static final String INSTRUCTION_FORMATS =
        "nop$ aconst_null$L iconst_m1$I iconst_0$I iconst_1$I "+
        "iconst_2$I iconst_3$I iconst_4$I iconst_5$I lconst_0$J_ "+
        "lconst_1$J_ fconst_0$F fconst_1$F fconst_2$F dconst_0$D_ "+
        "dconst_1$D_ bipush=bx$I sipush=bxx$I ldc=bk$X ldc_w=bkk$X "+
        "ldc2_w=bkk$X_ iload=bl/wbll$I lload=bl/wbll$J_ fload=bl/wbll$F "+
        "dload=bl/wbll$D_ aload=bl/wbll$L iload_0$I iload_1$I "+
        "iload_2$I iload_3$I lload_0$J_ lload_1$J_ lload_2$J_ "+
        "lload_3$J_ fload_0$F fload_1$F fload_2$F fload_3$F dload_0$D_ "+
        "dload_1$D_ dload_2$D_ dload_3$D_ aload_0$L aload_1$L "+
        "aload_2$L aload_3$L iaload$LI$I laload$LI$J_ faload$LI$F "+
        "daload$LI$D_ aaload$LI$L baload$LI$I caload$LI$I saload$LI$I "+
        "istore=bl/wbll$I$ lstore=bl/wbll$J_$ fstore=bl/wbll$F$ "+
        "dstore=bl/wbll$D_$ astore=bl/wbll$L$ istore_0$I$ istore_1$I$ "+
        "istore_2$I$ istore_3$I$ lstore_0$J_$ lstore_1$J_$ "+
        "lstore_2$J_$ lstore_3$J_$ fstore_0$F$ fstore_1$F$ fstore_2$F$ "+
        "fstore_3$F$ dstore_0$D_$ dstore_1$D_$ dstore_2$D_$ "+
        "dstore_3$D_$ astore_0$L$ astore_1$L$ astore_2$L$ astore_3$L$ "+
        "iastore$LII$ lastore$LIJ_$ fastore$LIF$ dastore$LID_$ "+
        "aastore$LIL$ bastore$LII$ castore$LII$ sastore$LII$ pop$X$ "+
        "pop2$XX$ dup$X$XX dup_x1$XX$XXX dup_x2$XXX$XXXX dup2$XX$XXXX "+
        "dup2_x1$XXX$XXXXX dup2_x2$XXXX$XXXXXX swap$XX$XX "+
        "iadd$II$I ladd$J_J_$J_ fadd$FF$F dadd$D_D_$D_ isub$II$I "+
        "lsub$J_J_$J_ fsub$FF$F dsub$D_D_$D_ imul$II$I lmul$J_J_$J_ "+
        "fmul$FF$F dmul$D_D_$D_ idiv$II$I ldiv$J_J_$J_ fdiv$FF$F "+
        "ddiv$D_D_$D_ irem$II$I lrem$J_J_$J_ frem$FF$F drem$D_D_$D_ "+
        "ineg$I$I lneg$J_$J_ fneg$F$F dneg$D_$D_ ishl$II$I lshl$J_I$J_ "+
        "ishr$II$I lshr$J_I$J_ iushr$II$I lushr$J_I$J_ iand$II$I "+
        "land$J_J_$J_ ior$II$I lor$J_J_$J_ ixor$II$I lxor$J_J_$J_ "+
        "iinc=blx/wbllxx$ i2l$I$J_ i2f$I$F i2d$I$D_ l2i$J_$I l2f$J_$F "+
        "l2d$J_$D_ f2i$F$I f2l$F$J_ f2d$F$D_ d2i$D_$I d2l$D_$J_ "+
        "d2f$D_$F i2b$I$I i2c$I$I i2s$I$I lcmp fcmpl fcmpg dcmpl dcmpg "+
        "ifeq=boo ifne=boo iflt=boo ifge=boo ifgt=boo ifle=boo "+
        "if_icmpeq=boo if_icmpne=boo if_icmplt=boo if_icmpge=boo "+
        "if_icmpgt=boo if_icmple=boo if_acmpeq=boo if_acmpne=boo "+
        "goto=boo jsr=boo ret=bl/wbll tableswitch=* lookupswitch=* "+
        "ireturn lreturn freturn dreturn areturn return "+
        "getstatic=bkf$Q putstatic=bkf$Q$ getfield=bkf$L$Q "+
        "putfield=bkf$LQ$ invokevirtual=bkm$LQ$Q "+
        "invokespecial=bkm$LQ$Q invokestatic=bkm$Q$Q "+
        "invokeinterface=bkixx$LQ$Q invokedynamic=bkd__$Q$Q new=bkc$L "+
        "newarray=bx$I$L anewarray=bkc$I$L arraylength$L$I athrow "+
        "checkcast=bkc$L$L instanceof=bkc$L$I monitorenter$L "+
        "monitorexit$L wide=* multianewarray=bkcx ifnull=boo "+
        "ifnonnull=boo goto_w=boooo jsr_w=boooo ";
    private static final String[] INSTRUCTION_NAMES;
    private static final String[] INSTRUCTION_POPS;
    private static final int[] INSTRUCTION_INFO;
    static {
        String[] insns = INSTRUCTION_FORMATS.split(" ");
        assert(insns[LOOKUPSWITCH].startsWith("lookupswitch"));
        assert(insns[TABLESWITCH].startsWith("tableswitch"));
        assert(insns[WIDE].startsWith("wide"));
        assert(insns[INVOKEDYNAMIC].startsWith("invokedynamic"));
        int[] info = new int[256];
        String[] names = new String[256];
        String[] pops = new String[256];
        for (int i = 0; i < insns.length; i++) {
            String insn = insns[i];
            int dl = insn.indexOf('$');
            if (dl > 0) {
                String p = insn.substring(dl+1);
                if (p.indexOf('$') < 0)  p = "$" + p;
                pops[i] = p;
                insn = insn.substring(0, dl);
            }
            int eq = insn.indexOf('=');
            if (eq < 0) {
                info[i] = 1;
                names[i] = insn;
                continue;
            }
            names[i] = insn.substring(0, eq);
            String fmt = insn.substring(eq+1);
            if (fmt.equals("*")) {
                info[i] = 0;
                continue;
            }
            int sl = fmt.indexOf('/');
            if (sl < 0) {
                info[i] = (char) fmt.length();
            } else {
                String wfmt = fmt.substring(sl+1);
                fmt = fmt.substring(0, sl);
                info[i] = (char)( fmt.length() + (wfmt.length() * 16) );
            }
        }
        INSTRUCTION_INFO = info;
        INSTRUCTION_NAMES = names;
        INSTRUCTION_POPS = pops;
    }

    public static class Instruction implements Cloneable {
        byte[] codeBase;
        int pc;
        int bc;
        int info;
        int wide;
        int len;
        Instruction(byte[] codeBase, int pc) {
            this.codeBase = codeBase;
            init(pc);
        }
        public Instruction clone() {
            try {
                return (Instruction) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new InternalError();
            }
        }
        private Instruction init(int pc) {
            this.pc = pc;
            this.bc = codeBase[pc] & 0xFF;
            this.info = INSTRUCTION_INFO[bc];
            this.wide = 0;
            this.len = (info & 0x0F);
            if (len == 0)
                computeLength();
            return this;
        }
        Instruction next() {
            if (len == 0 && bc != 0)  throw new InternalError();
            int npc = pc + len;
            if (npc == codeBase.length)
                return null;
            return init(npc);
        }
        void forceNext(int newLen) {
            bc = NOP;
            len = newLen;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(pc).append(":").append(INSTRUCTION_NAMES[bc]);
            switch (len) {
            case 3: buf.append(" ").append(u2At(1)); break;
            case 5: buf.append(" ").append(u2At(1)).append(" ").append(u2At(3)); break;
            default:  for (int i = 1; i < len; i++)  buf.append(" ").append(u1At(1));
            }
            return buf.toString();
        }

        // these are the hard parts
        private void computeLength() {
            int cases;
            switch (bc) {
            case WIDE:
                bc = codeBase[pc + 1];
                info = INSTRUCTION_INFO[bc];
                len = ((info >> 4) & 0x0F);
                if (len == 0)  throw new RuntimeException("misplaced wide bytecode: "+bc);
                return;

            case TABLESWITCH:
                cases = (u4At(alignedIntOffset(2)) - u4At(alignedIntOffset(1)) + 1);
                len = alignedIntOffset(3 + cases*1);
                return;

            case LOOKUPSWITCH:
                cases = u4At(alignedIntOffset(1));
                len = alignedIntOffset(2 + cases*2);
                return;

            default:
                throw new RuntimeException("unknown bytecode: "+bc);
            }
        }
        // switch code
        // clget the Nth int (where 0 is the first after the opcode itself)
        public int alignedIntOffset(int n) {
            int pos = pc + 1;
            pos += ((-pos) & 0x03);  // align it
            pos += (n * 4);
            return pos - pc;
        }
        public int u1At(int pos) {
            return (codeBase[pc+pos] & 0xFF);
        }
        public int u2At(int pos) {
            return (u1At(pos+0)<<8) + u1At(pos+1);
        }
        public int u4At(int pos) {
            return (u2At(pos+0)<<16) + u2At(pos+2);
        }
        public void u1AtPut(int pos, int x) {
            codeBase[pc+pos] = (byte)x;
        }
        public void u2AtPut(int pos, int x) {
            codeBase[pc+pos+0] = (byte)(x >> 8);
            codeBase[pc+pos+1] = (byte)(x >> 0);
        }
    }

    static String simplifyType(String type) {
        String simpleType = OBJ_SIGNATURE.matcher(type).replaceAll("L");
        assert(simpleType.matches("^\\([A-Z]*\\)[A-Z]$"));
        // change (DD)D to (D_D_)D_
        simpleType = WIDE_SIGNATURE.matcher(simpleType).replaceAll("\\0_");
        return simpleType;
    }
    static int argsize(String type) {
        return simplifyType(type).length()-3;
    }
    private static final Pattern OBJ_SIGNATURE = Pattern.compile("\\[*L[^;]*;|\\[+[A-Z]");
    private static final Pattern WIDE_SIGNATURE = Pattern.compile("[JD]");
}
