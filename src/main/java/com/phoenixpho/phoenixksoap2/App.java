package com.phoenixpho.phoenixksoap2;

import com.google.common.base.Predicate;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.reflections.Reflections;
import org.reflections.scanners.ConvertersScanner;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

/**
 * Hello world!
 *
 */
public class App 
{
    private class Serializer{
        public String attrName;
        public String classname;
        public String body;
    }
    
    // cache enum types 
    public static final String pack = "com.phoenix.soap.beans";
    public static final String packDest = "com.phoenix.soap.entities";
    public static Set<String> enumTypes = new HashSet<String>();
    // serializer name, serializer body
    public static Map<String, String> vectorSerializers = new HashMap<String, String>();
    public static final Map<String, String> returnMap;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
        aMap.put("java.lang.String",    "PropertyInfo.STRING_CLASS");
        aMap.put("java.lang.Long",      "PropertyInfo.STRING_CLASS");
        aMap.put("long",                "PropertyInfo.STRING_CLASS");
//        aMap.put("int",                 "PropertyInfo.STRING_CLASS");
//        aMap.put("java.lang.Integer",   "PropertyInfo.STRING_CLASS");        
//        aMap.put("java.lang.Long",      "PropertyInfo.LONG_CLASS");
//        aMap.put("long",                "PropertyInfo.LONG_CLASS");
        aMap.put("int",                 "PropertyInfo.INTEGER_CLASS");
        aMap.put("java.lang.Integer",   "PropertyInfo.INTEGER_CLASS");
        aMap.put("Integer",             "PropertyInfo.INTEGER_CLASS");
        aMap.put("boolean",             "PropertyInfo.BOOLEAN_CLASS");
        aMap.put("Boolean",             "PropertyInfo.BOOLEAN_CLASS");
        aMap.put("java.util.Date",      "java.util.Date");
        returnMap = Collections.unmodifiableMap(aMap);
    }
    
    public static String generateWrapperName(Class<?> en){
        String n = en.getSimpleName();
        n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        return n+"VectorSerializer";
    }
    
    /**
     * Generates wrapper for vector of elements en
     * @param en
     * @return 
     */
    public static String reconstructVectorWrapper(Class<?> en, String className, String fieldName, String registerAdd){
        StringBuilder sb = new StringBuilder();
        String t = en.getCanonicalName().replace(pack, packDest);
        String n = en.getSimpleName();
        String infoType = t;
        
        if (enumTypes.contains(t)){
            infoType = "PropertyInfo.STRING_CLASS;";
        } else if (t.startsWith(packDest)){
            infoType = t + ".class";
        } else if (returnMap.containsKey(t)){
            infoType = returnMap.get(t);
        } else {
            infoType = t + ".class";
        }
        
        n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        sb.append(
  "package "+packDest+"; \n\n"                
+ "import java.util.Hashtable; \n"
+ "import java.util.Vector; \n\n"
+ "import com.phoenix.soap.SoapEnvelopeRegisterable;\n"
+ "import org.ksoap2.serialization.SoapSerializationEnvelope;\n"
+ "import org.ksoap2.serialization.KvmSerializable;\n"
+ "import org.ksoap2.serialization.PropertyInfo;\n\n"
+ "public class "+className+" extends Vector<"+t+"> implements KvmSerializable, SoapEnvelopeRegisterable { \n"
+ "    private static final long serialVersionUID = 1L;  // you can let the IDE generate this \n"
+ "    // Whether to allow return null size from getPropertyCount().\n"
+ "    // If yes then de-serialization won't work.\n"
+ "    private boolean allowReturnNullSize = false;   \n\n"
+ getterSetterRaw("allowReturnNullSize", "AllowReturnNullSize", "boolean") + "\n\n"
+ "    @Override\n"
+ "    public Object getProperty(int index) {\n"
+ "        return this.get(index);\n"
+ "    }\n\n"
+ "    @Override \n"
+ "    public int getPropertyCount() { \n"
+ "        int i = this.size(); \n"
+ "        if (i==0 && this.allowReturnNullSize==false) return 1; \n"
+ "        return i;\n"
+ "    } \n\n"
+ "    @Override \n"
+ "    public void setProperty(int index, Object value) { \n"
+ "        this.add(("+t+") value); \n"
+ "    } \n\n"
+ "    @Override \n"
+ "    public void getPropertyInfo(int index, Hashtable properties, PropertyInfo info) { \n"
+ "        info.name = \""+fieldName+"\"; \n"
+ "        info.type = "+infoType+"; \n"
+ "        info.setNamespace(com.phoenix.soap.ServiceConstants.NAMESPACE); \n"                
+ "    } \n\n"
+ "    @Override \n"
+ "    public void register(SoapSerializationEnvelope soapEnvelope) { \n"
+ "        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+en.getSimpleName()+"\", "+t+".class);\n"
+ "        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+fieldName+"\", "+t+".class);\n"
);
        // additional registration entries
        if(registerAdd!=null){
            sb.append(registerAdd).append("\n");
        }
        
        // recursive call for subtype from package
        if (en.getCanonicalName().startsWith(pack)){
            sb.append(
  "        new "+en.getSimpleName()+"().register(soapEnvelope);\n");
        }
        
        sb.append(
  "    }"                        
+ "}\n");
        return sb.toString();
    }
    
    public static String reconstructClass(Class<?> en){
        StringBuilder sb = new StringBuilder();
        String can = en.getCanonicalName();
        boolean hasByteArray=false;
        boolean hasDate=false;
        boolean hasDouble=false;
        boolean hasFloat=false;
        // 
        Map<String, String> attributesFromSamePackage = new HashMap<String, String>();
        
        // Map from list to wrapper
        Map<String, String> wrappers = new HashMap<String, String>();
        Set<String> currVectorizers = new HashSet<String>();
        // classes from same package, not enum
        List<Class<?>> internalClasses = new LinkedList<Class<?>>();
        Map<String, Class<?>> wrappersCls = new HashMap<String, Class<?>>();
        
        // attribute name to index
        Map<String, Integer> attr2idx = new HashMap<String, Integer>();
        
        // declaration
        sb.append("package "+packDest+";\n\n");
        sb.append("import com.phoenix.soap.SoapEnvelopeRegisterable;\n");
        sb.append("import java.util.Hashtable;\n");
        //sb.append("import org.spongycastle.util.encoders.Base64;\n");
        sb.append("import org.ksoap2.serialization.SoapSerializationEnvelope;\n");
        sb.append("import org.ksoap2.serialization.KvmSerializable;\n");
        sb.append("import org.ksoap2.serialization.PropertyInfo;\n\n\n");
        
        sb.append("public class ").append(en.getSimpleName()).append(" implements KvmSerializable, SoapEnvelopeRegisterable {\n\n");
        sb.append("    public final static String NAMESPACE = \"http://phoenix.com/hr/schemas\";\n");
        
        // workaround for null vector serializer - if empty, do not serialize them
        sb.append("    protected boolean ignoreNullWrappers = false;\n");
        
        // fields declaration
        Field[] fields = en.getDeclaredFields();
        for(int i = 0, sz = fields.length; i < sz; i++){
            Field fld = fields[i];
            String f = fld.getName();
            Class<?> ftype = fld.getType();
            String t = ftype.getCanonicalName();
            
            // return transform
            if ("_return".equals(f)){
                f="return";
            }
            
            // special serializers
            if ("byte[]".equalsIgnoreCase(t)){
                hasByteArray=true;
            } else if("java.util.Date".equalsIgnoreCase(t)){
                hasDate=true;
            } else if("java.lang.Double".equalsIgnoreCase(t)){
                hasDouble=true;
            } else if("java.lang.Float".equalsIgnoreCase(t)){
                hasFloat=true;
            }
            
            if (t.startsWith(pack)){
                attributesFromSamePackage.put(f, t.replace(pack, packDest));
            }
            
            if (enumTypes.contains(t)== false && t.startsWith(pack)){
                internalClasses.add(ftype);
            }
            
            String type = ftype.getCanonicalName().replace(pack, packDest);
            
            // list - convert to vector
            if ("java.util.List".equalsIgnoreCase(t)){
                // obtain parametrized type
                Type genType = fld.getGenericType();
                if (genType instanceof ParameterizedType){
                    final ParameterizedType pt = (ParameterizedType) genType;
                    Class<?> partype = (Class<?>) pt.getActualTypeArguments()[0];
                    System.out.println("    parametrizedType: " + partype.getSimpleName()); // class java.lang.String.
                
                    
                    // special feature:
                    // fixing asymmetry between JAXB and KSOAP2 comprehension of 
                    // arrays and serialization. JAXB interprets SEQUENCE with auxiliary
                    // class which is redundant in ksoap and causes problems. Thus SEQUENCE
                    // type is now directly used.
                    
                    // get XML ROOT annotation - obtain real name to register
                    XmlRootElement rootElem = en.getAnnotation(XmlRootElement.class);
            
                    // register self class
                    String registerAdd = null;
                    if (rootElem!=null){
                        registerAdd = 
  "        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+rootElem.name()+"\", "+(en.getCanonicalName().replace(pack, packDest))+".class);\n";
                    }
                    
                    // if has only one field called _return -> skip wrapper, do it directly
                    if (fields.length==1){
                        System.out.println("SEQUENCE element (generating with reconstructWrapper), elem: " + en.getSimpleName());
                        
                        String wrapperBody = reconstructVectorWrapper(partype, en.getSimpleName(), f, registerAdd);
                        wrappers.put(f, en.getSimpleName());
                        wrappersCls.put(f, partype);
                        return wrapperBody;
                    } else {
                        // generate wrapper name
                        String wrapperName = generateWrapperName(partype);
                        wrappers.put(f, wrapperName);
                        wrappersCls.put(f, partype);
                        // generate wrapper 
                        String wrapperBody = reconstructVectorWrapper(partype, wrapperName, f, null);
                        attr2idx.put(f, Integer.valueOf(i));
                        vectorSerializers.put(wrapperName, wrapperBody);
                        currVectorizers.add(wrapperName);
                        type = wrapperName;
                    }
                }
            }
            
            attr2idx.put(fld.getName(), Integer.valueOf(i));
            sb.append("    protected ")
                    .append(type)
                    .append(" ")
                    .append(fld.getName())
                    .append(";\n");
        }
        
        sb.append("\n\n");
        // getters&setters
        for(Field fld : fields){
            Class<?> type = fld.getType();
            if (wrappers.containsKey(fld.getName())){
                sb.append(getterSetter(fld, wrappers.get(fld.getName()))).append("\n");
            } else {
                sb.append(getterSetter(fld, type.getCanonicalName().replace(pack, packDest))).append("\n");
            }
        }
        
        // add getter/setter for ignoreNullWrapper workaround
        sb.append("\n").append(getterSetterRaw("ignoreNullWrappers", "IgnoreNullWrappers", "boolean")).append("\n");
        
        // kvm serializable implementation
        sb.append("\n    @Override \n"
+"    public int getPropertyCount() { \n");
        
        if (wrappers.isEmpty()){
            sb.append(
 "        return "+fields.length+";\n");
        } else {
            sb.append(
 "        int length = " + fields.length + ";\n"
+"        if (this.ignoreNullWrappers==false) return length;\n");
            // generate vector wrapper related code
            for(Entry<String,String> e : wrappers.entrySet()){
                sb.append(
 "        if (this.").append(e.getKey()).append("==null) length-=1;\n");
            }
            sb.append(
 "        return length; \n");
        }
        
        sb.append(
 "    } \n"
+"\n\n");
        
        // ignoreNullWrapperShift()
        // used to ignore null wrappers during serialization so as not to produce
        // lists with one empty instance
        sb.append(
  "    /**\n"
+ "     * Computes index shift for serialization methods in order to ignore null\n"
+ "     * wrappers during serialization so as not to produce lists with one empty\n"
+ "     * instance\n"
+ "     */\n"
+ "     protected int ignoreNullWrapperShift(int idx) {\n"
        );
        // for null wrappers ignore this
        if (wrappers.isEmpty()){
            sb.append(
  "        return idx;\n");
        } else {
            sb.append(
  "        int i = idx;\n"
+ "        if (this.ignoreNullWrappers==false) return i;\n");
            for(Entry<String,String> e : wrappers.entrySet()){
                String attr = e.getKey();
                int idxOfWrapper = attr2idx.get(attr);
                sb.append(
  "        if (i==").append(idxOfWrapper).append(" && this.").append(attr).append("==null) i+=1;\n");
            }
            sb.append(
  "        return i;\n");
        }
        sb.append(
  "    }\n\n");
        
        // getProperty
        sb.append(
  "     /*\n"
+ "      * (non-Javadoc)\n"
+ "      * \n"
+ "      * @see org.ksoap2.serialization.KvmSerializable#getProperty(int)\n"
+ "      */\n"
+ "    @Override\n"
+ "    public Object getProperty(int index) {\n"
+ "        index = this.ignoreNullWrapperShift(index);\n"
+ "        switch (index){\n");
        for(int i = 0, sz = fields.length; i < sz; i++){
            Field fld = fields[i];
            String n = fld.getName();
            Class<?> ftype = fld.getType();
            sb.append(
"            case " + i + ":\n");
            // is defined enum?
            if (enumTypes.contains(ftype.getCanonicalName())){
                sb.append(
"                return this." + n + " == null ? null : this." + n + ".toString().toLowerCase();\n");
            } else {
                sb.append(
"                return this." + n + ";\n");
            }
        }
        sb.append(
"            default:\n"
+ "                return null;\n"
+ "        }\n"
+ "    }\n\n");
        
        
        // get property info
        sb.append(
  "     /*\n"
+ "      * (non-Javadoc)\n"
+ "      * \n"
+ "      * @see org.ksoap2.serialization.KvmSerializable#getPropertyInfo(int,\n"
+ "      * java.util.Hashtable, org.ksoap2.serialization.PropertyInfo)\n"                
+ "      */\n"
+ "    @Override\n"
+ "    public void getPropertyInfo(int index, Hashtable arg1, PropertyInfo info) {\n"
+ "        index = this.ignoreNullWrapperShift(index);\n"
+ "        switch (index){\n");
        for(int i = 0, sz = fields.length; i < sz; i++){
            Field fld = fields[i];
            String n = fld.getName();
            Class<?> ftype = fld.getType();
            String c = ftype.getCanonicalName();
            
            sb.append(
"            case " + i + ":\n");
            
            sb.append(
  "                // type: " + c + "\n"
+ "                info.name = \""+n+"\";\n"
+ "                info.setNamespace(com.phoenix.soap.ServiceConstants.NAMESPACE);\n");
            //info.type = PropertyInfo.STRING_CLASS;
            // is enum?
            if (enumTypes.contains(c)){
                sb.append(
  "                info.type = PropertyInfo.STRING_CLASS;\n");
            } else if (c.startsWith(pack)){
                sb.append(
  "                info.type = "+(c.replace(pack, packDest))+".class;\n");
            } else if (returnMap.containsKey(c)){
                String transform = returnMap.get(c);
                sb.append(
  "                info.type = "+transform+";\n");
            } else if (wrappers.containsKey(n)){
                // has serializer
                sb.append(
  "                info.type = "+wrappers.get(n)+".class;\n");
            } else {
                sb.append(
  "                info.type = PropertyInfo.STRING_CLASS;\n");
            }
            sb.append(
"                break;\n");
        }
        sb.append(
"            default:\n"
+ "                break;\n"
+ "        }\n"
+ "    }\n\n");

        
        // set property
        sb.append(
  "     /*\n"
+ "      * (non-Javadoc)\n"
+ "      * \n"
+ "      * @see org.ksoap2.serialization.KvmSerializable#setProperty(int,\n"
+ "      * java.lang.Object)\n"                
+ "      */\n"
+ "     @Override\n"
+ "     public void setProperty(int index, Object arg1) {\n"
+ "        index = this.ignoreNullWrapperShift(index);\n"
+ "        switch (index){\n");
        for(int i = 0, sz = fields.length; i < sz; i++){
            Field fld = fields[i];
            String n = fld.getName();
            Class<?> ftype = fld.getType();
            String c = ftype.getCanonicalName();
            System.out.println("   Fld: " + n + "; type: [" + c + "]"); 
            
            sb.append(
  "            case " + i + ":\n"
+ "                // type: " + c + "\n");
            // is enum?
            if (enumTypes.contains(c)){
                sb.append(
  "                this."+n+" = "+(c.replace(pack, packDest))+".fromValue((String) arg1);\n");
            } else if ("byte[]".equalsIgnoreCase(c)) {
                // byte, can be base64 encoded or raw
                sb.append(
  "                if (arg1 instanceof String){ \n"
+ "                    final String tmp = (String) arg1;\n"
+ "                    if (tmp==null || tmp.isEmpty())\n"
+ "                        this."+n+" = null; \n"
+ "                    else\n"
+ "                        this."+n+" = org.spongycastle.util.encoders.Base64.decode(tmp);\n"
+ "                } else if (arg1 instanceof byte[]){\n"
+ "                    this."+n+" = (byte[]) arg1;\n"
+ "                } else { \n"
+ "                    throw new IllegalArgumentException(\"Format unknown\"); \n"
+ "                }\n");
            } else if ("java.util.Date".equalsIgnoreCase(c)){
                sb.append(
  "                DateFormat formatter = new SimpleDateFormat(\"MM/dd/yy\"); \n"
+ "                Date date=null; \n"
+ "                try { \n"
+ "                    date = formatter.parse((String) arg1); \n"
+ "                } catch (ParseException e) { \n"
+ "                    Log.e(\"GetOneTimeTokenResponse\", \"Problem with date parsing\", e); \n"
+ "                } \n"
+ "                \n"
+ "                this."+n+" = date;  \n");
            } else if ("int".equalsIgnoreCase(c)) {
                sb.append(
  "                this."+n+" = Integer.parseInt(arg1.toString());\n");
            } else if ("long".equalsIgnoreCase(c)) {
                sb.append(
  "                this."+n+" = Long.parseLong(arg1.toString());\n");          
            } else if ("boolean".equalsIgnoreCase(c)) {
                sb.append(
  "                this."+n+" = Boolean.parseBoolean(arg1.toString());\n");
            } else if (wrappers.containsKey(n)){
                sb.append(
  "                this."+n+" = ("+wrappers.get(n)+") arg1;\n");
            } else {
                sb.append(
  "                this."+n+" = ("+(c.replace(pack, packDest))+")arg1;\n");
            }
            sb.append(
"                break;\n");
        }
        sb.append(
"            default:\n"
+ "                return;\n"
+ "        }\n"
+ "    }\n\n");   
        
        // register envelope - if has special types registered
        sb.append("    @Override \n"
+"    public void register(SoapSerializationEnvelope soapEnvelope) { \n");
            if (hasByteArray){
                sb.append("        new org.ksoap2.serialization.MarshalBase64().register(soapEnvelope);\n");
            }
            
            if (hasDate){
                sb.append("        new org.ksoap2.serialization.MarshalDate().register(soapEnvelope);\n");
            }
            
            if (hasDouble || hasFloat){
                sb.append("        new org.ksoap2.serialization.MarshalFloat().register(soapEnvelope);\n");
            }
            
            // store already registered objects to avoid duplicates
            Set<String> registeredObjects = new HashSet<String>();
            
            // get XML ROOT annotation - obtain real name to register
            XmlRootElement rootElem = en.getAnnotation(XmlRootElement.class);
            System.out.println(" ----------> ROOTELEM: " + rootElem);
            
            // register self class
            if (rootElem!=null){
                registeredObjects.add(rootElem.name());
                sb.append("        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+rootElem.name()+"\", "+(en.getCanonicalName().replace(pack, packDest))+".class);\n");
            }
            
            // any subclass from this package present?
            Set<Entry<String, String>> entrySet = attributesFromSamePackage.entrySet();
            for(Entry<String, String> e : entrySet){
                registeredObjects.add(e.getKey());
                sb.append("        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+e.getKey()+"\", "+e.getValue()+".class);\n");
            }
            
            // wrappers
            Set<Entry<String, String>> entrySet1 = wrappers.entrySet();
            for(Entry<String, String> e : entrySet1){     
                if (registeredObjects.contains(e.getValue())==false) {
                    registeredObjects.add(e.getValue());
                    sb.append("        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+e.getValue()+"\", "+e.getValue()+".class);\n"); 
                }
                
                if (vectorSerializers.containsKey(e.getKey())){
                    sb.append("        new "+e.getValue()+"().register(soapEnvelope);\n");
                }
            }
            
            // call recursively for classes from same package
            for(Class<?> ftype : internalClasses){
                if (registeredObjects.contains(ftype.getSimpleName())) continue;
                
                sb.append("        soapEnvelope.addMapping(com.phoenix.soap.ServiceConstants.NAMESPACE, \""+ftype.getSimpleName()+"\", "+(ftype.getCanonicalName().replace(pack, packDest))+".class);\n");
            }
            
            for(String c : currVectorizers){
                sb.append("        new "+c+"().register(soapEnvelope);\n");
            }
            
         sb.append("    } \n\n");
         
         //toString
         sb.append(
  "    @Override\n"
+ "    public String toString() {\n"
+ "        return ");
         sb.append('"').append(en.getSimpleName()).append("{\"");
         for(int i = 0, sz = fields.length; i < sz; i++){
            Field fld = fields[i];
            String n = fld.getName();
            
            sb.append("+\"");
            if (i>0) sb.append(", ");
            sb.append(n).append("=\" + this.").append(n);
         }
         sb.append(" + '}';\n"
+ "    }\n");
        //return "UserIdentifier{" + "userSIP=" + userSIP + ", userID=" + userID + '}';
    
        
        // end class
        sb.append("}\n");
        return sb.toString();
    }
    
    /**
     * Reconstructs enum 
     * @param en 
     */
    public static String reconstructEnum(Class<?> en){
        StringBuilder sb = new StringBuilder();
        sb.append("package "+packDest+";\n\n");
        sb.append("public enum ").append(en.getSimpleName()).append(" {\n\n");
        
        String can = en.getCanonicalName();
        
        // extract fields
        Field[] fields = en.getDeclaredFields();
        List<Field> enumFields = new ArrayList<Field>();
        for(Field fld : fields){
            String f = fld.getName();
            Class<?> ftype = fld.getType();
            if (can.equals(ftype.getCanonicalName())==false){
                continue;
            }
            
            enumFields.add(fld);
        }
        
        ListIterator<Field> it = enumFields.listIterator();
        for(int i=0, sz = enumFields.size(); it.hasNext(); i++){
            Field fld = it.next();
            sb.append("\t")
                    .append(fld.getName())
                    .append("(\"")
                    .append(fld.getName().toLowerCase())
                    .append("\")");
            if (i+1 == sz) sb.append(";\n");
            else sb.append(",\n");
        }
        
        // value
        sb.append("\tprivate final String value;\n\n");
        
        // constructors and methods
        sb.append("\t").append(en.getSimpleName()).append("(String v) { \n")
                .append("\t\tvalue=v;\n")
                .append("\t}\n\n")
                .append("\tpublic String value() {\n")
                .append("\t\treturn value;\n")
                .append("\t}\n\n")
                .append("\tpublic static ").append(en.getSimpleName()).append(" fromValue(String v) { \n")
                .append("\t\tfor (").append(en.getSimpleName())
                .append(" c: ")
                .append(en.getSimpleName())
                .append(".values()) { \n"
+ "            \tif (c.value.equals(v)) { \n"
+ "                \t\treturn c; \n"
+ "            } \n"
+ "        } \n"
+ "        throw new IllegalArgumentException(v); \n"
+ "    } \n"
+ "} \n");
        
        return sb.toString();
    }
    
    /**
     * Generates getter setter 
     * @param fld
     * @return 
     */
    public static String getterSetter(Field fld, String tc){
        //StringBuilder sb = new StringBuilder();
        Class<?> ftype = fld.getType();
        String n = fld.getName();
        String N = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        //String tc = ftype.getCanonicalName().replace(pack, packDest);
        
        return getterSetterRaw(n, N, tc);
    }
    
    /**
     * Generate raw getter and setter from passed variables
     * @param n attribute name
     * @param N function name for getter and setter - attribute suffix, first upper
     * @param tc type of attribute
     * @return 
     */
    public static String getterSetterRaw(String n, String N, String tc){
        StringBuilder sb = new StringBuilder();
        sb.append(
"    /**\n" +
"     * Gets the value of the "+n+" property.\n" +
"     * \n" +
"     * @return\n" +
"     *     possible object is\n" +
"     *     {@link "+tc+" }\n" +
"     *     \n" +
"     */\n" +
"    public "+tc+" get"+N+"() {\n" +
"        return "+n+";\n" +
"    }\n" +
"\n" +
"    /**\n" +
"     * Sets the value of the "+n+" property.\n" +
"     * \n" +
"     * @param value\n" +
"     *     allowed object is\n" +
"     *     {@link "+tc+" }\n" +
"     *     \n" +
"     */\n" +
"    public void set"+N+"("+tc+" value) {\n" +
"        this."+n+" = value;\n" +
"    }");
        
        return sb.toString();
    }
    
    
    public static void main( String[] args )
    {        
        System.out.println( "Hello World!" );
        Reflections.collect();
        
        //Reflections reflections = new Reflections("com.phoenix.soap.beans");
         Predicate<String> filter = new FilterBuilder().include("com.phoenix.soap.beans\\$.*");
         //Predicate<String> filter = new FilterBuilder().include("com.phoenix.soap.beans.*");
         Reflections reflections = new Reflections(new ConfigurationBuilder()
                .filterInputsBy(filter)
                .setScanners(
                        new SubTypesScanner().filterResultsBy(filter),
                        new TypeAnnotationsScanner().filterResultsBy(filter),
                        new FieldAnnotationsScanner().filterResultsBy(filter),
                        new MethodAnnotationsScanner().filterResultsBy(filter),
                        new ConvertersScanner().filterResultsBy(filter)));
         
        Reflections.collect();
        reflections = new Reflections(pack);
        Reflections.collect();
        
        Set<Class<? extends Object>> allClasses =  reflections.getSubTypesOf(Object.class);
        
        System.out.println("Classes found: " + allClasses.size());
        for(Class<? extends Object> cls : allClasses){
            System.out.println("Class: " + cls.getCanonicalName());
        }
        
        System.out.println("Classes: " + reflections.getSubTypesOf(Object.class));
        Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(XmlType.class);
        // cache enums
        for(Class<?> cls : typesAnnotatedWith){            
            // determine is enum?
            if (cls.isEnum()){
                enumTypes.add(cls.getCanonicalName());
            }
        }
        
        for(Class<?> cls : typesAnnotatedWith){
            System.out.println("Class: " + cls.getCanonicalName());
            
            Field[] fields = cls.getDeclaredFields();
            for(Field fld : fields){
                Class<?> ftype = fld.getType();
                Type genType = fld.getGenericType();
                
                System.out.println("  field: " + fld.getName() + "; type: " + ftype.getCanonicalName());
                if (genType instanceof ParameterizedType){
                    final ParameterizedType pt = (ParameterizedType) genType;
                    Class<?> stringListClass = (Class<?>) pt.getActualTypeArguments()[0];
                    System.out.println("    parametrizedType: " + stringListClass.getSimpleName()); // class java.lang.String.
                }
            }
            
            /*Reflections tmpRefl = new Reflections(cls);
            Set<Field> fields = tmpRefl.getFieldsAnnotatedWith(XmlElement.class);
            System.out.println("Fields: " + fields);
            for(Field fld : fields){
                Class<?> ftype = fld.getType();
                System.out.println("  field: " + fld.getName() + "; type: " + ftype.getName());
            }*/
            
            // determine is enum?
            String body = "";
            if (cls.isEnum()){
                body = reconstructEnum(cls);
                //System.out.println("<ENUM>");
                //System.out.println(body);
                //System.out.println("</ENUM>");
            } else {
                body = reconstructClass(cls);
                //System.out.println("<CLASS>");
                //System.out.println(body);
                //System.out.println("</CLASS>");
            }
            
            if (body.isEmpty()){
                System.out.println("Empty body");
                continue;
            }
            
            String fname = cls.getSimpleName() + ".java";
            if (    false
                    //|| "CertificateStatus.java".equals(fname)
                    //|| "CertificateWrapper.java".equals(fname)
                    || "GetOneTimeTokenRequest.java".equals(fname)
                    || "GetOneTimeTokenResponse.java".equals(fname)
                    || "SignCertificateRequest.java".equals(fname)
                    || "SignCertificateResponse.java".equals(fname)
                    ){
                System.out.println("Ignoring: " + fname);
                continue;
            }
            
            try {
                // Create file 
                FileWriter fstream = new FileWriter("/tmp/classes/" + fname);
                BufferedWriter out = new BufferedWriter(fstream);
                out.write(body);
                out.close();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
            
            
            /*Field[] fields = cls.getDeclaredFields();
            for(Field fld : fields){
                Class<?> ftype = fld.getType();
                System.out.println("  field: " + fld.getName() + "; type: " + ftype.getCanonicalName());
            */
        }
        
        // write wrappers
        System.out.println("Wrappers");
        Set<Entry<String, String>> entrySet = vectorSerializers.entrySet();
        for(Entry<String, String> e : entrySet){
            try {
                // Create file 
                System.out.println("Wrapper: " + e.getKey());
                FileWriter fstream = new FileWriter("/tmp/classes/" + e.getKey() + ".java");
                BufferedWriter out = new BufferedWriter(fstream);
                out.write(e.getValue());
                out.close();
            } catch (Exception ex) {
                System.err.println("Error: " + ex.getMessage());
            }
        }
    }
}
