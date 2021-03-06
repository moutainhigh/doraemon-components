package org.doraemon.component.mybatis.component;

import org.apache.ibatis.session.SqlSession;
import org.doraemon.component.mybatis.dao.TableMapper;
import org.doraemon.component.mybatis.enums.DatabaseType;
import org.doraemon.component.mybatis.model.*;
import org.doraemon.component.mybatis.util.FreemarkerUtils;
import org.doraemon.component.mybatis.util.MyBatisUtils;
import org.doraemon.component.mybatis.util.SqlHelper;
import org.doraemon.component.mybatis.util.StringHelper;
import org.doraemon.component.mybatis.vo.ColumnVO;
import org.doraemon.component.mybatis.vo.TableVO;
import org.doraemon.framework.Constants;
import org.doraemon.framework.util.IOUtils;
import org.doraemon.framework.util.PropertyUtils;
import org.doraemon.framework.util.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * Description: mybatis生成工具组件
 * Author:      fengwenping
 * Date:        2019/8/3 22:10
 */
public class GeneratorComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratorComponent.class);

    private GeneratorComponent() {
    }

    private static GeneratorComponent instance = new GeneratorComponent();

    public static GeneratorComponent getInstance() {
        return instance;
    }

    private Properties configProperties = new Properties();

    private DatabaseType databaseType = GeneratorProperties.DEFAULT_DB_TYPE;

    private Map<String, EntityModel> models = new HashMap<>(10);

    private void init() {
        try {
            final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(GeneratorProperties.CONFIG_FILE_NAME);
            String xml = IOUtils.copyToString(inputStream, Constants.CharsetConfig.utf8Charset());
            final TableConfiguration configuration = XmlUtils.convertXml2Object(TableConfiguration.class, xml, Constants.CharsetConfig.utf8Charset().name());
            final List<ConfigProperty> properties = configuration.getProperties();
            properties.forEach(configProperty -> configProperties.setProperty(configProperty.getName(), configProperty.getValue()));
            this.databaseType = DatabaseType.fromString(PropertyUtils.getString(configProperties, GeneratorProperties.DB_TYPE).toLowerCase());
            final List<ConfigTable> tables = configuration.getTables();
            tables.forEach(table -> {
                final EntityModel entityModel = new EntityModel(PropertyUtils.getString(configProperties, GeneratorProperties.PACKAGE_NAME),
                        PropertyUtils.getString(configProperties, GeneratorProperties.MODEL_PREFIX) + table.getModel() + PropertyUtils.getString(configProperties, GeneratorProperties.MODEL_SUFFIX),
                        table.getName()
                        , null);
                models.put(table.getName().toUpperCase(), entityModel);
            });
        } catch (Exception e) {
            LOGGER.error("init table config failure", e);
        }
    }

    private void generate() {
        List<TableVO> tables = new ArrayList<>();
        final SqlSession sqlSession = MyBatisUtils.openSqlSession();
        try {
            final TableMapper tableMapper = MyBatisUtils.getMapper(sqlSession, TableMapper.class);
            tables = tableMapper.findTables(PropertyUtils.getString(this.configProperties, GeneratorProperties.DB_SCHEMA), new ArrayList<>(this.models.keySet()));
        } catch (Exception e) {
            LOGGER.error("查询数据库表结构失败.", e);
        } finally {
            MyBatisUtils.closeSqlSession(sqlSession);
        }
        tables.forEach(tableVO -> {
            EntityModel entityModel = this.models.get(tableVO.getTableName().toUpperCase());
            entityModel.setComment(tableVO.getTableComment());
            final List<ColumnVO> columns = tableVO.getColumns();
            columns.forEach(columnVO -> {
                FieldModel fieldModel = new FieldModel();
                fieldModel.setColumnName(columnVO.getColumnName());
                fieldModel.setName(StringHelper.underLine2Camel(columnVO.getColumnName()));
                fieldModel.setComment(columnVO.getColumnComment());
                fieldModel.setJdbcType(columnVO.getDataType());
                fieldModel.setJavaType(SqlHelper.getJavaType(this.databaseType, columnVO.getDataType()));
                entityModel.getFields().add(fieldModel);
            });
        });
        this.generateFile(databaseType.getCode().toLowerCase() + "/" + GeneratorProperties.JAVA_MODEL_TEMPLATE_NAME);
        this.generateFile(databaseType.getCode().toLowerCase() + "/" + GeneratorProperties.XML_MAPPER_TEMPLATE_NAME);
        this.generateFile(databaseType.getCode().toLowerCase() + "/" + GeneratorProperties.JAVA_MAPPER_TEMPLATE_NAME);
    }

    private void generateFile(String templateName) {
        this.models.values().forEach(entityModel -> {
            final Map<String, Object> data = new FreemarkerUtils.DataModelBuilder().build();
            data.put("model", entityModel);
            final String html = FreemarkerUtils.renderHtml(templateName, data);
            LOGGER.info("html: {}", html);
        });
    }

    public String getOutputDirectory(String outputDirectory, String packageName) {
        if (outputDirectory == null) {
            outputDirectory = "/";
        }
        if (packageName == null) {
            packageName = "";
        }
        if (packageName.contains("/") || packageName.contains("\\")) {
            packageName = packageName.replace("/", ".").replace("\\", ".");
        }
        while (packageName.contains("..")) {
            packageName = packageName.replace("..", ".");
        }
        if (packageName.startsWith(".")) {
            packageName = packageName.substring(1, packageName.length() - 1);
        }
        if (packageName.endsWith(".")) {
            packageName = packageName.substring(0, packageName.length() - 1);
        }
        outputDirectory = outputDirectory + "/" + packageName.replace(".", "/");
        return outputDirectory.replace("\\", "/");
    }

    public void createFile(String outputDirectory, String fileName, String content) throws IOException {
        if (fileName == null || fileName.equals("")) {
            throw new IllegalArgumentException("fileName must be not null");
        }
        fileName = outputDirectory + "/" + fileName;
        File file = new File(fileName);
        if (!(file.getParentFile().exists())) {
            boolean flag = file.getParentFile().mkdirs();

        }
        if (file.exists()) {
            boolean flag = file.delete();
        }
        Writer writer = new FileWriter(file, true);
        writer.write(content);
        writer.close();
    }

    public void execute() {
        final GeneratorComponent component = GeneratorComponent.getInstance();
        component.init();
        component.generate();
    }
}
