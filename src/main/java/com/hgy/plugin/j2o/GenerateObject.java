package com.hgy.plugin.j2o;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.hgy.plugin.inspired.Inflector;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.codemodel.*;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Generated;
import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * 包含从给定的 JSON 文本生成 Java POJO 类的代码。
 *
 * @author hgy
 * @since 2023-12-24 18:37:05
 */
class GenerateObject {
    /**
     * 始终注释暴露
     */
    private static final boolean ALWAYS_ANNOTATE_EXPOSE = false;
    /**
     * 模块资源根
     */
    private final VirtualFile moduleSourceRoot;
    /**
     * 包名
     */
    private final String packageName;
    /**
     * 进度条
     */
    private final ProgressIndicator progressBar;

    /**
     * 类名映射类
     */
    private final Map<String, JDefinedClass> classMap = new HashMap<>();
    private JType deferredClass;
    private JType deferredList;
    /**
     * 字段比较器
     */
    private FieldComparator fieldComparator;
    /**
     * 类映射字段列表
     */
    private final Map<JDefinedClass, Set<FieldInfo>> fieldMap = new HashMap<>();

    /**
     * 构造器
     *
     * @param packageName      包名
     * @param moduleSourceRoot 模块资源根
     * @param progressBar      进度条
     */
    GenerateObject(String packageName, VirtualFile moduleSourceRoot, ProgressIndicator progressBar) {
        this.moduleSourceRoot = moduleSourceRoot;
        this.packageName = packageName;
        this.progressBar = progressBar;
    }

    /**
     * Generates POJOs from a source JSON text.
     *
     * @param rootName         the name of the root class to generate.
     * @param json             the source JSON text.
     * @param generateBuilders true if the generated class should omit setters and generate a builder instead.
     * @param useMPrefix       true if the generated fields should use an 'm' prefix.
     */
    void generateFromJson(String rootName, String json, boolean generateBuilders, boolean useMPrefix) {
        fieldComparator = new FieldComparator(useMPrefix);

        try {
            // Create code model and package
            JCodeModel jCodeModel = new JCodeModel();
            JPackage jPackage = jCodeModel._package(packageName);

            // Create deferrable types
            deferredClass = jCodeModel.ref(Deferred.class);
            deferredList = jCodeModel.ref(List.class).narrow(Deferred.class);

            // Parse the JSON data
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(json);

            // Recursively generate
            generate(rootNode, formatClassName(rootName), jPackage, generateBuilders, useMPrefix);

            // Build
            jCodeModel.build(new File(moduleSourceRoot.getPath()));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.toString(), "Codegen Failed", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 生成给定类的所有子对象和字段。
     *
     * @param rootNode         JSON 语法树中的 JSON 类节点。
     * @param rootName         要生成的根类的名称。
     * @param jPackage         生成类的代码模型包。
     * @param generateBuilders true 如果生成的类应省略设置器，而生成一个生成器。
     * @param useMPrefix       如果生成的字段应使用 "m "前缀，则为 true。
     * @throws Exception 异常
     */
    private void generate(JsonNode rootNode, String rootName, JPackage jPackage, boolean generateBuilders,
        boolean useMPrefix) throws Exception {
        // 首先创建所有引用子类型并收集字段数据
        parseObject(rootNode, rootName, jPackage);

        // 现在创建实际字段
        int idx = 1;
        for (JDefinedClass clazz : classMap.values()) {
            // 生成字段
            List<GeneratedField> fields =
                generateFields(clazz, fieldMap.get(clazz), jPackage.owner(), generateBuilders, useMPrefix);

            // 可选择生成内部生成器类
            if (generateBuilders) {
                generateBuilder(clazz, fields);
            }

            // 更新进度
            progressBar.setFraction((double)idx / (double)classMap.size());
            idx++;
        }
    }

    /**
     * 生成给定类的所有子对象。
     *
     * @param classNode JSON 语法树中的 JSON 对象节点。
     * @param className 要为该节点创建的类的名称。
     * @param jPackage  生成类的代码模型包。
     * @throws Exception 异常
     */
    private void parseObject(JsonNode classNode, String className, JPackage jPackage) throws Exception {
        // 如果存在，则查找该类；如果不存在，则创建该类
        JDefinedClass clazz;
        if (classMap.containsKey(className)) {
            clazz = classMap.get(className);
        } else {
            clazz = jPackage._class(className);
            annotateClass(clazz);
            classMap.put(className, clazz);
            fieldMap.put(clazz, new TreeSet<>(fieldComparator));
        }

        // 遍历该对象的所有字段
        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = classNode.fields();
        while (fieldsIterator.hasNext()) {
            // 获取字段名称和子节点
            Map.Entry<String, JsonNode> entry = fieldsIterator.next();
            String childProperty = entry.getKey();
            JsonNode childNode = entry.getValue();

            // 向对象和数组递归
            if (childNode.isObject()) {
                String childName = formatClassName(childProperty);
                parseObject(childNode, childName, jPackage);
            } else if (childNode.isArray()) {
                String childName = formatClassName(Inflector.getInstance().singularize(childProperty));
                parseArray(childNode, childName, jPackage);
            }

            // 现在尝试创建字段并将其添加到字段集中
            FieldInfo field = getFieldInfoFromNode(childNode, childProperty, jPackage.owner());
            if (field != null) {
                fieldMap.get(clazz).add(field);
            }
        }
    }

    /**
     * 生成给定数组节点的所有子对象。
     *
     * @param arrayNode 树中的 JSON 数组节点。
     * @param className 的格式化名称。
     * @param jPackage  生成类的代码模型包。
     * @throws Exception 异常
     */
    private void parseArray(JsonNode arrayNode, String className, JPackage jPackage) throws Exception {
        // 读取数组中第一个非空元素
        Iterator<JsonNode> elementsIterator = arrayNode.elements();
        while (elementsIterator.hasNext()) {
            JsonNode element = elementsIterator.next();

            // 对第一个对象或数组进行递归
            if (element.isObject()) {
                parseObject(element, className, jPackage);
                break;
            } else if (element.isArray()) {
                parseArray(element, className, jPackage);
                break;
            }
        }
    }

    /**
     * 在给定的类中创建一个字段。
     *
     * @param node         描述字段的 JSON 节点。
     * @param propertyName 要创建的字段的名称。
     * @param jCodeModel   生成代码时使用的代码模型。
     * @return {@link FieldInfo} 代表新字段。
     * @throws Exception 异常
     */
    private FieldInfo getFieldInfoFromNode(JsonNode node, String propertyName, JCodeModel jCodeModel) throws Exception {
        // 开启节点类型
        if (node.isArray()) {
            // 将单个元素的类名单一化
            String newClassName = formatClassName(Inflector.getInstance().singularize(propertyName));

            // 获取数组类型
            if (node.elements().hasNext()) {
                JsonNode firstNode = node.elements().next();
                if (firstNode.isObject()) {
                    // 从类映射中获取已创建的类
                    JDefinedClass newClass = classMap.get(newClassName);

                    // 现在返回指向新类别列表的字段
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(newClass), propertyName);
                } else if (firstNode.isArray()) {
                    // 递归获取该节点的字段信息
                    FieldInfo fi = getFieldInfoFromNode(firstNode, propertyName, jCodeModel);

                    // 创建递归类型的 List<>
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(fi.Type), propertyName);
                } else if (firstNode.isFloatingPointNumber()) {
                    // 现在返回引用双倍列表的字段
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(Double.class), propertyName);
                } else if (firstNode.isIntegralNumber()) {
                    // 现在返回指向 long 列表的字段
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(Long.class), propertyName);
                } else if (firstNode.isNull()) {
                    // 空值？返回 List<Deferred>。
                    return new FieldInfo(deferredList, propertyName);
                } else if (firstNode.isTextual()) {
                    // 现在返回引用字符串列表的字段
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(String.class), propertyName);
                }
            } else {
                // 没有元素？返回 List<Deferred>。
                return new FieldInfo(deferredList, propertyName);
            }
        } else if (node.isBoolean()) {
            return new FieldInfo(jCodeModel.ref(Boolean.class), propertyName);
        } else if (node.isFloatingPointNumber()) {
            return new FieldInfo(jCodeModel.ref(Double.class), propertyName);
        } else if (node.isIntegralNumber()) {
            return new FieldInfo(jCodeModel.ref(Long.class), propertyName);
        } else if (node.isNull()) {
            // 将类型引用推迟到以后
            return new FieldInfo(deferredClass, propertyName);
        } else if (node.isObject()) {
            // 从类映射中获取已创建的类
            String newClassName = formatClassName(propertyName);
            JDefinedClass newClass = classMap.get(newClassName);

            // 现在将字段作为已定义的类返回
            return new FieldInfo(newClass, propertyName);
        } else if (node.isTextual()) {
            return new FieldInfo(jCodeModel.ref(String.class), propertyName);
        }

        // 如果其他操作都失败，则返回 null
        return null;
    }

    /**
     * 生成给定类的所有字段。
     *
     * @param clazz            类，为其生成子对象和字段。
     * @param fields           要生成的字段集。
     * @param jCodeModel       代码模型。
     * @param generateBuilders true 如果生成的类应省略设置器，而生成一个生成器。
     * @param useMPrefix       true 如果生成的类应省略设置器，而生成一个生成器。
     * @return 生成的字段列表。
     */
    private List<GeneratedField> generateFields(JDefinedClass clazz, Set<FieldInfo> fields, JCodeModel jCodeModel,
        boolean generateBuilders, boolean useMPrefix) {
        List<GeneratedField> generatedFields = new ArrayList<>();

        // 获取已排序的字段名列表
        for (FieldInfo fieldInfo : fields) {
            // 使用正确的命名方案创建字段
            String fieldName = formatFieldName(fieldInfo.PropertyName, useMPrefix);

            // 解析延迟类型
            JFieldVar newField;
            if (fieldInfo.Type.equals(deferredClass)) {
                // 尝试从类映射中获取类
                String newClassName = formatClassName(fieldInfo.PropertyName);
                JDefinedClass newClass = classMap.get(newClassName);

                // 现在返回实际类类型的字段
                if (newClass != null) {
                    newField = clazz.field(JMod.PRIVATE, newClass, fieldName);
                } else {
                    // 否则，只需创建一个对象类型的字段即可
                    newField = clazz.field(JMod.PRIVATE, jCodeModel.ref(Object.class), fieldName);
                }
            } else if (fieldInfo.Type.equals(deferredList)) {
                // 尝试从类映射中获取类
                String newClassName = formatClassName(Inflector.getInstance().singularize(fieldInfo.PropertyName));
                JDefinedClass newClass = classMap.get(newClassName);

                // 现在返回指向新类别列表的字段
                if (newClass != null) {
                    newField = clazz.field(JMod.PRIVATE, jCodeModel.ref(List.class).narrow(newClass), fieldName);
                } else {
                    // 否则，只需创建一个 List<Object> 类型的字段即可。
                    newField = clazz.field(JMod.PRIVATE, jCodeModel.ref(List.class).narrow(Object.class), fieldName);
                }
            } else {
                // 否则，只需创建一个 List<Object> 类型的字段即可。
                newField = clazz.field(JMod.PRIVATE, fieldInfo.Type, fieldName);
            }

            if (newField != null) {
                // 注释字段
                annotateField(newField, fieldInfo.PropertyName);

                // 创建获取器
                createGetter(clazz, newField, fieldInfo.PropertyName);

                // 仅在不生成生成器类时创建设置器方法
                if (!generateBuilders) {
                    createSetter(clazz, newField, fieldInfo.PropertyName);
                }

                // 将字段添加到返回列表
                generatedFields.add(new GeneratedField(newField, fieldInfo.PropertyName));
            }
        }

        return generatedFields;
    }

    /**
     * 生成包含类的内部构建器类，并为给定字段提供方法。
     *
     * @param clazz  类中生成生成器类。
     * @param fields 生成的字段列表。
     * @throws Exception 异常
     */
    private void generateBuilder(JDefinedClass clazz, List<GeneratedField> fields) throws Exception {
        // 先创建创建器
        JDefinedClass builder = clazz._class(JMod.PUBLIC | JMod.STATIC, "Builder");

        // 获取已排序的字段名列表
        for (GeneratedField generatedField : fields) {
            // 创建新字段
            builder.field(JMod.PRIVATE, generatedField.Field.type(), generatedField.Field.name());

            // 创建构建器设置方法
            createBuilderSetter(builder, generatedField.Field, generatedField.PropertyName);
        }

        // 创建构建方法
        createBuildMethod(clazz, builder, fields);
    }

    /**
     * 为类添加 {@link Generated} 注解。
     *
     * @param clazz 要注释的类。
     */
    private static void annotateClass(JDefinedClass clazz) {
        clazz.annotate(Generated.class).param("value", "net.hexar.Json2Object");
        clazz.annotate(SuppressWarnings.class).param("value", "unused");
    }

    /**
     * 添加{@link Expose} 注解和潜在的{@link SerializedName} 注解到给定的
     * 只有当属性名与字段名不同时，才会使用后者。
     *
     * @param field        要注释的字段。
     * @param propertyName 的原始 JSON 属性名称。
     */
    private static void annotateField(JFieldVar field, String propertyName) {
        // 如果字段名称与属性名称不匹配，则使用序列化名称注解
        if (!field.name().equals(propertyName)) {
            field.annotate(SerializedName.class).param("value", propertyName);

            // 如果我们总是添加 @Expose，那么也添加这个
            if (ALWAYS_ANNOTATE_EXPOSE) {
                field.annotate(Expose.class);
            }
        } else {
            // 否则，只需添加 @Expose
            field.annotate(Expose.class);
        }
    }

    /**
     * 为给定的类、字段和属性名称生成生成器方法。
     *
     * @param builder      类中生成生成器方法。
     * @param field        要设置的字段。
     * @param propertyName 要设置的字段。
     * @return 一个{@link JMethod}，它是给定字段的构建方法。
     */
    private static JMethod createBuilderSetter(JDefinedClass builder, JFieldVar field, String propertyName) {
        // 方法名称应以 "set "开头，然后是大写的类名
        JMethod withMethod = builder.method(JMod.PUBLIC, builder, "with" + formatClassName(propertyName));

        // 将参数名称设置为小写驼峰字母
        String paramName = sanitizePropertyName(propertyName);
        JVar param = withMethod.param(field.type(), paramName);

        // 分配给字段名
        JBlock body = withMethod.body();
        if (field.name().equals(paramName)) {
            // 指定 this.FieldName = paramName
            body.assign(JExpr._this().ref(field), param);
        } else {
            // 可以安全地直接指定 FieldName = paramName
            body.assign(field, param);
        }
        body._return(JExpr._this());
        return withMethod;
    }

    /**
     * 为构建器创建构建方法。
     *
     * @param owner   要构建的包含类。
     * @param builder 生成方法的构建器。
     * @param fields  的生成字段列表。
     */
    private JMethod createBuildMethod(JDefinedClass owner, JDefinedClass builder, List<GeneratedField> fields) {
        // 方法名称应以 "set "开头，然后是大写的类名
        JMethod buildMethod = builder.method(JMod.PUBLIC, owner, "build");

        // 分配给字段名
        JBlock body = buildMethod.body();

        // 声明所有者类的新实例
        String localName = sanitizePropertyName(owner.name());
        JVar local = body.decl(owner, localName, JExpr._new(owner));

        // 获取已排序的字段名列表
        for (GeneratedField field : fields) {
            // 在所有者类中分配字段
            body.assign(local.ref(field.Field.name()), JExpr.ref(field.Field.name()));
        }

        // 返回新实例
        body._return(local);
        return buildMethod;
    }

    /**
     * 为给定的类、字段和属性名称生成一个获取器。
     *
     * @param clazz        类中生成一个 getter。
     * @param field        要返回的字段。
     * @param propertyName 的名称。
     * @return 一个{@link JMethod}，它是给定字段的获取器。
     */
    private static JMethod createGetter(JDefinedClass clazz, JFieldVar field, String propertyName) {
        // 方法名称应以 "get "开头，然后是大写的类名
        JMethod getter = clazz.method(JMod.PUBLIC, field.type(), "get" + formatClassName(propertyName));

        // 返回字段
        JBlock body = getter.body();
        body._return(field);
        return getter;
    }

    /**
     * 为给定的类、字段和属性名称生成一个设置器。
     *
     * @param clazz        类中生成一个设置器。
     * @param field        要设置的字段。
     * @param propertyName 的名称。
     * @return 一个{@link JMethod}，它是给定字段的设置器。
     */
    private static JMethod createSetter(JDefinedClass clazz, JFieldVar field, String propertyName) {
        // 方法名称应以 "set "开头，然后是大写的类名
        JMethod setter = clazz.method(JMod.PUBLIC, void.class, "set" + formatClassName(propertyName));

        // 将参数名称设置为小写驼峰字母
        String paramName = sanitizePropertyName(propertyName);
        JVar param = setter.param(field.type(), paramName);

        // 分配给字段名
        JBlock body = setter.body();
        if (field.name().equals(paramName)) {
            // 指定 this.FieldName = paramName
            body.assign(JExpr._this().ref(field), param);
        } else {
            // 可以安全地直接指定 FieldName = paramName
            body.assign(field, param);
        }
        return setter;
    }

    /**
     * 将给定的属性名格式化为更标准的类名。
     *
     * @param propertyName 的原始属性名称。
     * @return 格式化的类名。
     */
    static String formatClassName(String propertyName) {
        return StringUtils.capitalize(sanitizePropertyName(propertyName));
    }

    /**
     * 将给定的属性名格式化为更标准的字段名。
     *
     * @param propertyName 的原始属性名称。
     * @param useMPrefix   如果字段名的前缀为 "m"，则为 true。
     * @return 格式化的字段名。
     */
    static String formatFieldName(String propertyName, boolean useMPrefix) {
        String fieldName = sanitizePropertyName(propertyName);

        if (useMPrefix) {
            fieldName = "m" + StringUtils.capitalize(fieldName);
        }
        return fieldName;
    }

    /**
     * 以字符串形式给出属性名称，通过删除非字母数字字符并将非字母数字字符后的字母大写，创建有效的标识符。
     *
     * @param propertyName 要格式化的属性名称。
     * @return 一个包含大写单词的字符串，去掉下划线。
     */
    private static String sanitizePropertyName(String propertyName) {
        final StringBuilder formattedName = new StringBuilder();
        boolean uppercaseNext = false;

        // 避免无效的类名/字段名起始字符
        if (Character.isJavaIdentifierStart(propertyName.charAt(0))) {
            formattedName.append(Character.toLowerCase(propertyName.charAt(0)));
        }

        // 避免无效的类名/字段名起始字符
        for (int charIndex = 1; charIndex < propertyName.length(); charIndex++) {
            // 追加有效字符
            Character c = propertyName.charAt(charIndex);
            if (Character.isAlphabetic(c)) {
                if (uppercaseNext) {
                    // 大写该字母
                    formattedName.append(Character.toUpperCase(c));
                    uppercaseNext = false;
                } else {
                    // 保留箱体，第一次下放
                    formattedName.append(formattedName.length() == 0 ? Character.toLowerCase(c) : c);
                }
            } else if (Character.isDigit(c)) {
                // 按原样追加
                formattedName.append(c);
            } else {
                // 不要追加非字母数字部分，并将下一个字母大写
                uppercaseNext = formattedName.length() > 0;
            }
        }

        return formattedName.toString();
    }

    /**
     * Class 类型，表示我们还不知道该字段代表的数据类型。
     */
    private static class Deferred {

    }

    /**
     * 比较器，按字段名对字段名数据对象进行排序，不区分大小写。
     */
    private static class FieldComparator implements Comparator<FieldInfo> {

        private final boolean mUseMPrefix;

        public FieldComparator(boolean useMPrefix) {
            mUseMPrefix = useMPrefix;
        }

        @Override
        public int compare(FieldInfo left, FieldInfo right) {
            // 按格式化的字段名而不是属性名排序
            return formatFieldName(left.PropertyName, mUseMPrefix).compareTo(
                formatFieldName(right.PropertyName, mUseMPrefix));
        }
    }

    /**
     * 要创建的字段的简单表示。
     */
    private static class FieldInfo {
        final JType Type;
        final String PropertyName;

        FieldInfo(JType type, String propertyName) {
            Type = type;
            PropertyName = propertyName;
        }
    }

    /**
     * 一个包含生成的 {@link JFieldVar} 字段及其原始属性名称的对。
     */
    private static class GeneratedField {
        final JFieldVar Field;
        final String PropertyName;

        GeneratedField(JFieldVar field, String propertyName) {
            Field = field;
            PropertyName = propertyName;
        }
    }
}
