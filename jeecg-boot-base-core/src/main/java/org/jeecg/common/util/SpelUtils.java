package org.jeecg.common.util;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpEL 表达式工具(第三章 9.7.4, 课件说从课程资料拷贝, 这里是自己写的)
 * 作用: 把 "ORDERS:CREATEWAYBILL:LOCK:#{orderId}" 里的 #{orderId} 替换成方法参数 orderId 的值
 */
public class SpelUtils {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private static final TemplateParserContext TEMPLATE = new TemplateParserContext("#{", "}");

    /** 匹配 #{ 后面紧跟的第一个名称, 例如 #{orderId} 里的 orderId、#{order.id} 里的 order */
    private static final Pattern FIRST_NAME = Pattern.compile("#\\{\\s*([A-Za-z_][A-Za-z0-9_]*)");

    private SpelUtils() {
    }

    /**
     * 解析表达式
     *
     * @param formatter  带 #{...} 的字符串
     * @param paramNames 方法参数名
     * @param args       方法参数值
     * @return 解析后的字符串; 没有 #{ 时原样返回
     */
    public static String parse(String formatter, String[] paramNames, Object[] args) {
        if (formatter == null || !formatter.contains("#{")) {
            return formatter;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        Set<String> names = new HashSet<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                // 按位置也能取: #{p0}、#{a0}
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
                names.add("p" + i);
                names.add("a" + i);
                if (paramNames != null && i < paramNames.length && paramNames[i] != null) {
                    context.setVariable(paramNames[i], args[i]);
                    names.add(paramNames[i]);
                }
            }
        }
        // 编译时如果没有保留参数名(拿不到 orderId 这样的名字), 方法又只有一个参数, 就把表达式里的名字都当成这个参数
        if (args != null && args.length == 1) {
            Matcher nameMatcher = FIRST_NAME.matcher(formatter);
            while (nameMatcher.find()) {
                String name = nameMatcher.group(1);
                if (!names.contains(name)) {
                    context.setVariable(name, args[0]);
                    names.add(name);
                }
            }
        }
        // SpEL 里取变量要写成 #orderId; 课件的写法是 #{orderId}, 这里自动补上 #, 两种写法都支持
        Matcher matcher = FIRST_NAME.matcher(formatter);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = names.contains(name) ? "#{#" + name : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        Expression expression = PARSER.parseExpression(sb.toString(), TEMPLATE);
        Object value = expression.getValue(context);
        return value == null ? "" : value.toString();
    }
}
