package org.Archibald.medator;

import org.Archibald.medator.impl.DefaultSqlSessionFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlSessionFactoryBuilder {

    public DefaultSqlSessionFactory build(String resourceName) throws DocumentException {
        Reader reader = Resource.getResourceAsReader(resourceName);                                 // 通过相对路径名获取文件Reader
        SAXReader saxReader = new SAXReader();                                                      // 通过dom4j处理xml文件
        Document document = saxReader.read(reader);
        Element root = document.getRootElement();
        Configuration configuration = parseConfiguration(root);

        return new DefaultSqlSessionFactory(configuration);
    }

    private Configuration parseConfiguration(Element root) {                                        // 从xml文件中生成配置类对象
        Configuration configuration = new Configuration();
        configuration.setDataSource(dataSource(root));                                              // 获取数据库配置信息
        configuration.setConnection(getConnection(configuration));                                  // 获取数据库连接
        configuration.setMapperSqlContext(getMapperSqlContext(root));                               // 获取sql上下文信息
        return configuration;
    }

    private Map<String, SqlContext> getMapperSqlContext (Element root) {
        Map<String, SqlContext> map = new HashMap<>();
        Element mappersElement = (Element) root.selectSingleNode("mappers");
        String regEx = "(#\\{(.*?)})";
        Pattern pattern = Pattern.compile(regEx);
        for (Element mapperElement : mappersElement.elements()) {
            String resourceName = mapperElement.attributeValue("resource");                      // 获取”resource“属性值
            SAXReader saxReader = new SAXReader();
            try {
                Document document = saxReader.read(Resource.getResourceAsReader(resourceName));
                Element rootElement = document.getRootElement();                                    // 根结点
                String namespace = rootElement.attributeValue("namespace");
                List<Node> selectNodes = rootElement.selectNodes("//select");
                for (Node selectNode : selectNodes) {
                    Element selectElement = (Element) selectNode;                                   // select元素
                    String key = namespace + "." + selectElement.attributeValue("id");

                    String sql = selectElement.getText();
                    Matcher matcher = pattern.matcher(sql);
                    int idx = 1;                                                                    // 记录占位符位置（从1开始）
                    Map<Integer, String> locationAndPlaceHolderName = new HashMap<>();
                    while (matcher.find()) {
                        String group1 = matcher.group(1);
                        String group2 = matcher.group(2);                                           // 记录占位符名称
                        sql = sql.replace(group1, "?");                                  // 将#{...}占位符换为?
                        locationAndPlaceHolderName.put(idx, group2);
                        idx++;
                    }

                    SqlContext sqlContext = new SqlContext();                                       // 构建sqlContext对象
                    sqlContext.setSql(sql);
                    sqlContext.setResultType(selectElement.attributeValue("resultType"));
                    sqlContext.setLocationAndPlaceholderName(locationAndPlaceHolderName);

                    map.put(key, sqlContext);
                }

            } catch (DocumentException e) {
                e.printStackTrace();
            }
        }
        return map;
    }

    /**
     * 配置数据库连接并返回
     * @param configuration 已获取的数据库配置信息
     * @return 数据库连接
     */
    private Connection getConnection (Configuration configuration) {
        Map<String, String> dataSourceMap = configuration.getDataSource();
        try {
            Class.forName(dataSourceMap.get("driver"));
            return DriverManager.getConnection(dataSourceMap.get("url"), dataSourceMap.get("username"), dataSourceMap.get("password"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取数据库配置信息
     * @param root xml文件根结点
     * @return 数据库配置信息key:name -> value:value
     */
    private Map<String, String> dataSource (Element root) {
        Map<String, String> dataSourceMap = new HashMap<>();
        Element dataSourceElement = (Element) root.selectSingleNode("//dataSource");                    // "//"表示全文搜索
        for (Element subElement : dataSourceElement.elements()) {
            String name = subElement.attributeValue("name");
            String value = subElement.attributeValue("value");
            dataSourceMap.put(name, value);
        }
        return dataSourceMap;
    }

}
