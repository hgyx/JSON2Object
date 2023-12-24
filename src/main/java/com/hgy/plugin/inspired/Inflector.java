/*
 * JBoss DNA (http://www.jboss.org/dna)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * JBoss DNA is free software. Unless otherwise indicated, all code in JBoss DNA
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * JBoss DNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.hgy.plugin.inspired;

import net.jcip.annotations.ThreadSafe;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将单词转换为单数、复数、人性化（人类可读）、下划线、驼峰或序数形式。其灵感来自 Inflector 类中
 * <a href="http://api.rubyonrails.org/classes/Inflector.html">Inflector</a>
 * <a href="http://www.rubyonrails.org">Ruby on Rails</a>
 * , which is distributed under the
 * <a href="http://wiki.rubyonrails.org/rails/pages/License">Rails license</a>.
 */
@ThreadSafe
public class Inflector {

    protected static final Inflector INSTANCE = new Inflector();

    public static final Inflector getInstance() {
        return INSTANCE;
    }

    protected class Rule {

        protected final String expression;
        protected final Pattern expressionPattern;
        protected final String replacement;

        protected Rule(String expression, String replacement) {
            this.expression = expression;
            this.replacement = replacement != null ? replacement : "";
            this.expressionPattern = Pattern.compile(this.expression, Pattern.CASE_INSENSITIVE);
        }

        /**
         * 针对输入字符串应用规则，返回修改后的字符串，如果规则不适用（未做任何修改），则返回空值
         *
         * @param input 输入字符串
         * @return 如果应用了该规则，则为修改后的字符串；如果输入未被该规则修改，则为空值
         */
        protected String apply(String input) {
            Matcher matcher = this.expressionPattern.matcher(input);
            if (!matcher.find())
                return null;
            return matcher.replaceAll(this.replacement);
        }

        @Override
        public int hashCode() {
            return expression.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj != null && obj.getClass() == this.getClass()) {
                final Rule that = (Rule)obj;
                if (this.expression.equalsIgnoreCase(that.expression))
                    return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return expression + ", " + replacement;
        }
    }

    private final LinkedList<Rule> plurals = new LinkedList<>();
    private final LinkedList<Rule> singulars = new LinkedList<>();

    /**
     * 要排除和不处理的小写单词。用户可通过 {@link #getUncountables()}修改此映射。
     */
    private final Set<String> uncountables = new HashSet<>();

    public Inflector() {
        initialize();
    }

    protected Inflector(Inflector original) {
        this.plurals.addAll(original.plurals);
        this.singulars.addAll(original.singulars);
        this.uncountables.addAll(original.uncountables);
    }

    @Override
    public Inflector clone() {
        return new Inflector(this);
    }

    /**
     * 返回字符串中单词的复数形式。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.pluralize(&quot;post&quot;)               #=&gt; &quot;posts&quot;
     *   inflector.pluralize(&quot;octopus&quot;)            #=&gt; &quot;octopi&quot;
     *   inflector.pluralize(&quot;sheep&quot;)              #=&gt; &quot;sheep&quot;
     *   inflector.pluralize(&quot;words&quot;)              #=&gt; &quot;words&quot;
     *   inflector.pluralize(&quot;the blue mailman&quot;)   #=&gt; &quot;the blue mailmen&quot;
     *   inflector.pluralize(&quot;CamelOctopus&quot;)       #=&gt; &quot;CamelOctopi&quot;
     * </pre>
     *
     * </p>
     * <p>
     * 请注意，如果在提供的对象上调用了 {@link Object#toString()}，那么此方法也适用于非字符串。
     * </p>
     *
     * @param word 要复数化的单词。
     * @return 该词的复数形式，或该词本身，如果它不能复数的话
     * @see #singularize(Object)
     */
    public String pluralize(Object word) {
        if (word == null)
            return null;
        String wordStr = word.toString().trim();
        if (wordStr.length() == 0)
            return wordStr;
        if (isUncountable(wordStr))
            return wordStr;
        for (Rule rule : this.plurals) {
            String result = rule.apply(wordStr);
            if (result != null)
                return result;
        }
        return wordStr;
    }

    public String pluralize(Object word, int count) {
        if (word == null)
            return null;
        if (count == 1 || count == -1) {
            return word.toString();
        }
        return pluralize(word);
    }

    /**
     * 返回字符串中单词的单数形式。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.singularize(&quot;posts&quot;)             #=&gt; &quot;post&quot;
     *   inflector.singularize(&quot;octopi&quot;)            #=&gt; &quot;octopus&quot;
     *   inflector.singularize(&quot;sheep&quot;)             #=&gt; &quot;sheep&quot;
     *   inflector.singularize(&quot;words&quot;)             #=&gt; &quot;word&quot;
     *   inflector.singularize(&quot;the blue mailmen&quot;)  #=&gt; &quot;the blue mailman&quot;
     *   inflector.singularize(&quot;CamelOctopi&quot;)       #=&gt; &quot;CamelOctopus&quot;
     * </pre>
     *
     * </p>
     * <p>
     * Note that if the {@link Object#toString()} is called on the supplied object, so this method works for non-strings, too.
     * </p>
     *
     * @param word 要复数化的单词。
     * @return 该词的复数形式，或该词本身，如果它不能复数的话
     * @see #pluralize(Object)
     */
    public String singularize(Object word) {
        if (word == null)
            return null;
        String wordStr = word.toString().trim();
        if (wordStr.length() == 0)
            return wordStr;
        if (isUncountable(wordStr))
            return wordStr;
        for (Rule rule : this.singulars) {
            String result = rule.apply(wordStr);
            if (result != null)
                return result;
        }
        return wordStr;
    }

    /**
     * 将字符串转换为小写字母。该方法还将使用任何额外的分隔符来识别单词边界。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.lowerCamelCase(&quot;active_record&quot;)       #=&gt; &quot;activeRecord&quot;
     *   inflector.lowerCamelCase(&quot;first_name&quot;)          #=&gt; &quot;firstName&quot;
     *   inflector.lowerCamelCase(&quot;name&quot;)                #=&gt; &quot;name&quot;
     *   inflector.lowerCamelCase(&quot;the-first_name&quot;,'-')  #=&gt; &quot;theFirstName&quot;
     * </pre>
     *
     * </p>
     *
     * @param lowerCaseAndUnderscoredWord 驼峰词
     * @param delimiterChars              用于分隔单词边界的可选字符
     * @return 驼峰
     * @see #underscore(String, char[])
     * @see #camelCase(String, boolean, char[])
     * @see #upperCamelCase(String, char[])
     */
    public String lowerCamelCase(String lowerCaseAndUnderscoredWord, char... delimiterChars) {
        return camelCase(lowerCaseAndUnderscoredWord, false, delimiterChars);
    }

    /**
     * 将字符串转换为大写字符串。该方法还将使用任何额外的分隔符来识别单词边界。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.upperCamelCase(&quot;active_record&quot;)       #=&gt; &quot;SctiveRecord&quot;
     *   inflector.upperCamelCase(&quot;first_name&quot;)          #=&gt; &quot;FirstName&quot;
     *   inflector.upperCamelCase(&quot;name&quot;)                #=&gt; &quot;Name&quot;
     *   inflector.lowerCamelCase(&quot;the-first_name&quot;,'-')  #=&gt; &quot;TheFirstName&quot;
     * </pre>
     *
     * </p>
     *
     * @param lowerCaseAndUnderscoredWord 驼峰词
     * @param delimiterChars              用于分隔单词边界的可选字符
     * @return 大驼峰
     * @see #underscore(String, char[])
     * @see #camelCase(String, boolean, char[])
     * @see #lowerCamelCase(String, char[])
     */
    public String upperCamelCase(String lowerCaseAndUnderscoredWord, char... delimiterChars) {
        return camelCase(lowerCaseAndUnderscoredWord, true, delimiterChars);
    }

    /**
     * 默认情况下，该方法将字符串转换为大写字母. 如果 <code>uppercaseFirstLetter</code> 参数为 false, 则该方法产生 lowerCamelCase. 这种方法还会使用任何额外的分隔符来识别单词边界。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.camelCase(&quot;active_record&quot;,false)    #=&gt; &quot;activeRecord&quot;
     *   inflector.camelCase(&quot;active_record&quot;,true)     #=&gt; &quot;ActiveRecord&quot;
     *   inflector.camelCase(&quot;first_name&quot;,false)       #=&gt; &quot;firstName&quot;
     *   inflector.camelCase(&quot;first_name&quot;,true)        #=&gt; &quot;FirstName&quot;
     *   inflector.camelCase(&quot;name&quot;,false)             #=&gt; &quot;name&quot;
     *   inflector.camelCase(&quot;name&quot;,true)              #=&gt; &quot;Name&quot;
     * </pre>
     *
     * </p>
     *
     * @param lowerCaseAndUnderscoredWord 驼峰词
     * @param uppercaseFirstLetter        如果第一个字符要大写，则为 true；如果第一个字符要小写，则为 false
     * @param delimiterChars              用于分隔单词边界的可选字符
     * @return 驼峰
     * @see #underscore(String, char[])
     * @see #upperCamelCase(String, char[])
     * @see #lowerCamelCase(String, char[])
     */
    public String camelCase(String lowerCaseAndUnderscoredWord, boolean uppercaseFirstLetter, char... delimiterChars) {
        if (lowerCaseAndUnderscoredWord == null)
            return null;
        lowerCaseAndUnderscoredWord = lowerCaseAndUnderscoredWord.trim();
        if (lowerCaseAndUnderscoredWord.length() == 0)
            return "";
        if (uppercaseFirstLetter) {
            String result = lowerCaseAndUnderscoredWord;
            // 在下一步转换下划线之前，用下划线替换多余的分隔符...
            if (delimiterChars != null) {
                for (char delimiterChar : delimiterChars) {
                    result = result.replace(delimiterChar, '_');
                }
            }

            // 在每个下划线后更改开头的大小写...
            return replaceAllWithUppercase(result, "(^|_)(.)", 2);
        }
        if (lowerCaseAndUnderscoredWord.length() < 2)
            return lowerCaseAndUnderscoredWord;
        return "" + Character.toLowerCase(lowerCaseAndUnderscoredWord.charAt(0)) + camelCase(
            lowerCaseAndUnderscoredWord, true, delimiterChars).substring(1);
    }

    /**
     * 将字符串中的表达式转换为下划线形式（与 {@link #camelCase(String, boolean, char[]) camelCase} 方法相反）。同时将与提供的分隔符匹配的字符改为下划线。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.underscore(&quot;activeRecord&quot;)     #=&gt; &quot;active_record&quot;
     *   inflector.underscore(&quot;ActiveRecord&quot;)     #=&gt; &quot;active_record&quot;
     *   inflector.underscore(&quot;firstName&quot;)        #=&gt; &quot;first_name&quot;
     *   inflector.underscore(&quot;FirstName&quot;)        #=&gt; &quot;first_name&quot;
     *   inflector.underscore(&quot;name&quot;)             #=&gt; &quot;name&quot;
     *   inflector.underscore(&quot;The.firstName&quot;)    #=&gt; &quot;the_first_name&quot;
     * </pre>
     *
     * </p>
     *
     * @param camelCaseWord  要转换的驼峰词；
     * @param delimiterChars 用于分隔单词边界的可选字符（除大小写外）
     * @return 输入内容的小写版本，用下划线分隔不同的单词。
     */
    public String underscore(String camelCaseWord, char... delimiterChars) {
        if (camelCaseWord == null)
            return null;
        String result = camelCaseWord.trim();
        if (result.length() == 0)
            return "";
        result = result.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        result = result.replaceAll("([a-z\\d])([A-Z])", "$1_$2");
        result = result.replace('-', '_');
        if (delimiterChars != null) {
            for (char delimiterChar : delimiterChars) {
                result = result.replace(delimiterChar, '_');
            }
        }
        return result.toLowerCase();
    }

    /**
     * 返回输入字符的副本，其中第一个字符转换为大写，其余字符转换为小写。
     *
     * @param words 要大写的词
     * @return 字符串，第一个字符大写，其余字符小写
     */
    public String capitalize(String words) {
        if (words == null)
            return null;
        String result = words.trim();
        if (result.length() == 0)
            return "";
        if (result.length() == 1)
            return result.toUpperCase();
        return "" + Character.toUpperCase(result.charAt(0)) + result.substring(1).toLowerCase();
    }

    /**
     * 像 {@link #titleCase(String, String[])}一样，它的目的是创建漂亮的输出。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.humanize(&quot;employee_salary&quot;)       #=&gt; &quot;Employee salary&quot;
     *   inflector.humanize(&quot;author_id&quot;)             #=&gt; &quot;Author&quot;
     * </pre>
     *
     * </p>
     *
     * @param lowerCaseAndUnderscoredWords 人性化输入
     * @param removableTokens              可选的要删除的标记数组
     * @return 人性化字符串
     * @see #titleCase(String, String[])
     */
    public String humanize(String lowerCaseAndUnderscoredWords, String... removableTokens) {
        if (lowerCaseAndUnderscoredWords == null)
            return null;
        String result = lowerCaseAndUnderscoredWords.trim();
        if (result.length() == 0)
            return "";
        // 删除尾部的"_id "标记
        result = result.replaceAll("_id$", "");
        // 删除所有应该删除的标记
        if (removableTokens != null) {
            for (String removableToken : removableTokens) {
                result = result.replaceAll(removableToken, "");
            }
        }
        // 用一个空格替换所有相邻的下划线
        result = result.replaceAll("_+", " ");
        return capitalize(result);
    }

    /**
     * 大写所有单词并替换字符串中的一些字符，以创建一个更美观的标题。将下划线改为空格，去掉尾部的"_id"，并去掉提供的任何标记。就像 {@link #humanize(String, String[])}一样，它的目的是创建漂亮的输出。
     * <p>
     * Examples:
     *
     * <pre>
     *   inflector.titleCase(&quot;man from the boondocks&quot;)       #=&gt; &quot;Man From The Boondocks&quot;
     *   inflector.titleCase(&quot;x-men: the last stand&quot;)        #=&gt; &quot;X Men: The Last Stand&quot;
     * </pre>
     *
     * </p>
     *
     * @param words           将输入内容转为标题
     * @param removableTokens 可选的要删除的标记数组
     * @return 所提供词语的大写字母
     */
    public String titleCase(String words, String... removableTokens) {
        String result = humanize(words, removableTokens);
        result = replaceAllWithUppercase(result, "\\b([a-z])", 1); // change first char of each word to uppercase
        return result;
    }

    /**
     * 将一个非负数转换成一个序数字符串，用于表示有序序列中的位置，如 1st、2nd、3rd、4th。
     *
     * @param number 非负数
     * @return 号和序号后缀的字符串
     */
    public String ordinalize(int number) {
        int remainder = number % 100;
        String numberStr = Integer.toString(number);
        if (11 <= number && number <= 13)
            return numberStr + "th";
        remainder = number % 10;
        if (remainder == 1)
            return numberStr + "st";
        if (remainder == 2)
            return numberStr + "nd";
        if (remainder == 3)
            return numberStr + "rd";
        return numberStr + "th";
    }

    // 管理方法

    /**
     * 通过{@link #pluralize(Object) pluralize}和{@link #singularize(Object) singularize}方法确定所提供的单词是否不可数。
     *
     * @param word 词
     * @return 如果单词的复数形式和单数形式相同，则为真
     */
    public boolean isUncountable(String word) {
        if (word == null)
            return false;
        String trimmedLower = word.trim().toLowerCase();
        return this.uncountables.contains(trimmedLower);
    }

    /**
     * 获取未被 Inflector 处理的单词集。得到的映射图可以直接修改。
     *
     * @return 不可数词集
     */
    public Set<String> getUncountables() {
        return uncountables;
    }

    public void addPluralize(String rule, String replacement) {
        final Rule pluralizeRule = new Rule(rule, replacement);
        this.plurals.addFirst(pluralizeRule);
    }

    public void addSingularize(String rule, String replacement) {
        final Rule singularizeRule = new Rule(rule, replacement);
        this.singulars.addFirst(singularizeRule);
    }

    public void addIrregular(String singular, String plural) {
        if (singular == null || singular.isEmpty()) {
            throw new IllegalArgumentException("singular");
        }
        if (plural == null || plural.isEmpty()) {
            throw new IllegalArgumentException("plural");
        }
        String singularRemainder = singular.length() > 1 ? singular.substring(1) : "";
        String pluralRemainder = plural.length() > 1 ? plural.substring(1) : "";
        addPluralize("(" + singular.charAt(0) + ")" + singularRemainder + "$", "$1" + pluralRemainder);
        addSingularize("(" + plural.charAt(0) + ")" + pluralRemainder + "$", "$1" + singularRemainder);
    }

    public void addUncountable(String... words) {
        if (words == null || words.length == 0)
            return;
        for (String word : words) {
            if (word != null)
                uncountables.add(word.trim().toLowerCase());
        }
    }

    /**
     * 实用方法，将特定反向引用给出的所有出现替换为其大写形式，并移除所有其他反向引用。
     * <p>
     * Java {@link Pattern 正则表达式处理}不使用预处理指令 <code>\l</code> 、
     * <code>&#92;u</code>, <code>\L</code>, and <code>\U</code>.
     * 如果是这样，就可以在替换字符串中使用这些指令，以大写或小写反向引用。例如
     * <code>\L1</code> 会小写第一个反向引用，而
     * <code>&#92;u3</code> 会将第 3 个反向参照大写。
     * </p>
     *
     * @param input                  输入
     * @param regex                  正则
     * @param groupNumberToUppercase 组数字到大写
     * @return 输入字符串，并将相应字符转换为大写字母
     */
    protected static String replaceAllWithUppercase(String input, String regex, int groupNumberToUppercase) {
        Pattern underscoreAndDotPattern = Pattern.compile(regex);
        Matcher matcher = underscoreAndDotPattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(groupNumberToUppercase).toUpperCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 完全删除该语气词中的所有规则。
     */
    public void clear() {
        this.uncountables.clear();
        this.plurals.clear();
        this.singulars.clear();
    }

    protected void initialize() {
        Inflector inflect = this;
        inflect.addPluralize("$", "s");
        inflect.addPluralize("s$", "s");
        inflect.addPluralize("(ax|test)is$", "$1es");
        inflect.addPluralize("(octop|vir)us$", "$1i");
        inflect.addPluralize("(octop|vir)i$", "$1i"); // already plural
        inflect.addPluralize("(alias|status)$", "$1es");
        inflect.addPluralize("(bu)s$", "$1ses");
        inflect.addPluralize("(buffal|tomat)o$", "$1oes");
        inflect.addPluralize("([ti])um$", "$1a");
        inflect.addPluralize("([ti])a$", "$1a"); // already plural
        inflect.addPluralize("sis$", "ses");
        inflect.addPluralize("(?:([^f])fe|([lr])f)$", "$1$2ves");
        inflect.addPluralize("(hive)$", "$1s");
        inflect.addPluralize("([^aeiouy]|qu)y$", "$1ies");
        inflect.addPluralize("(x|ch|ss|sh)$", "$1es");
        inflect.addPluralize("(matr|vert|ind)ix|ex$", "$1ices");
        inflect.addPluralize("([m|l])ouse$", "$1ice");
        inflect.addPluralize("([m|l])ice$", "$1ice");
        inflect.addPluralize("^(ox)$", "$1en");
        inflect.addPluralize("(quiz)$", "$1zes");
        // 需要检查下列已经复数化的单词：
        inflect.addPluralize("(people|men|children|sexes|moves|stadiums)$", "$1"); // irregulars
        inflect.addPluralize("(oxen|octopi|viri|aliases|quizzes)$", "$1"); // special rules

        inflect.addSingularize("s$", "");
        inflect.addSingularize("(s|si|u)s$", "$1s"); // '-us' and '-ss' are already singular
        inflect.addSingularize("(n)ews$", "$1ews");
        inflect.addSingularize("([ti])a$", "$1um");
        inflect.addSingularize("((a)naly|(b)a|(d)iagno|(p)arenthe|(p)rogno|(s)ynop|(t)he)ses$", "$1$2sis");
        inflect.addSingularize("(^analy)ses$", "$1sis");
        inflect.addSingularize("(^analy)sis$", "$1sis"); // already singular, but ends in 's'
        inflect.addSingularize("([^f])ves$", "$1fe");
        inflect.addSingularize("(hive)s$", "$1");
        inflect.addSingularize("(tive)s$", "$1");
        inflect.addSingularize("([lr])ves$", "$1f");
        inflect.addSingularize("([^aeiouy]|qu)ies$", "$1y");
        inflect.addSingularize("(s)eries$", "$1eries");
        inflect.addSingularize("(m)ovies$", "$1ovie");
        inflect.addSingularize("(x|ch|ss|sh)es$", "$1");
        inflect.addSingularize("([m|l])ice$", "$1ouse");
        inflect.addSingularize("(bus)es$", "$1");
        inflect.addSingularize("(o)es$", "$1");
        inflect.addSingularize("(shoe)s$", "$1");
        inflect.addSingularize("(cris|ax|test)is$", "$1is"); // already singular, but ends in 's'
        inflect.addSingularize("(cris|ax|test)es$", "$1is");
        inflect.addSingularize("(octop|vir)i$", "$1us");
        inflect.addSingularize("(octop|vir)us$", "$1us"); // already singular, but ends in 's'
        inflect.addSingularize("(alias|status)es$", "$1");
        inflect.addSingularize("(alias|status)$", "$1"); // already singular, but ends in 's'
        inflect.addSingularize("^(ox)en", "$1");
        inflect.addSingularize("(vert|ind)ices$", "$1ex");
        inflect.addSingularize("(matr)ices$", "$1ix");
        inflect.addSingularize("(quiz)zes$", "$1");

        inflect.addIrregular("person", "people");
        inflect.addIrregular("man", "men");
        inflect.addIrregular("child", "children");
        inflect.addIrregular("sex", "sexes");
        inflect.addIrregular("move", "moves");
        inflect.addIrregular("stadium", "stadiums");

        inflect.addUncountable("equipment", "information", "rice", "money", "species", "series", "fish", "sheep");
    }

}