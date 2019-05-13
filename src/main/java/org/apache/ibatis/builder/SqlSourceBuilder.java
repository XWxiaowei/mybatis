/*
 *    Copyright 2009-2012 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */
/**
 * SQL源码构建器
 * 
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String parameterProperties = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
//   创建#{} 占位符处理器
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    //替换#{}中间的部分,如何替换，逻辑在ParameterMappingTokenHandler
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
//  解析#{}占位符，并返回解析结果
    String sql = parser.parse(originalSql);
    //封装解析结果到StaticSqlSource中，并返回
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  //参数映射记号处理器，静态内部类
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    private List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
    private Class<?> parameterType;
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    public String handleToken(String content) {
      //获取context的对应的ParameterMapping
      parameterMappings.add(buildParameterMapping(content));
      //如何替换很简单，永远是一个问号，但是参数的信息要记录在parameterMappings里面供后续使用
      return "?";
    }

    //构建参数映射
    private ParameterMapping buildParameterMapping(String content) {
        //#{favouriteSection,jdbcType=VARCHAR}
        //先解析参数映射,就是转化成一个hashmap
      /*
     * 将 #{xxx} 占位符中的内容解析成 Map。大家可能很好奇一个普通的字符串是怎么解析成 Map 的，
     * 举例说明一下。如下：
     *
     *    #{age,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}
     *
     * 上面占位符中的内容最终会被解析成如下的结果：
     *
     *  {
     *      "property": "age",
     *      "typeHandler": "MyTypeHandler",
     *      "jdbcType": "NUMERIC",
     *      "javaType": "int"
     *  }
     *
     * parseParameterMapping 内部依赖 ParameterExpression 对字符串进行解析，ParameterExpression 的
     * 逻辑不是很复杂，这里就不分析了。大家若有兴趣，可自行分析
     */
      Map<String, String> propertiesMap = parseParameterMapping(content);
      String property = propertiesMap.get("property");
      Class<?> propertyType;
      // metaParameters 为 DynamicContext 成员变量 bindings 的元信息对象
      if (metaParameters.hasGetter(property)) {
        /*
     * parameterType 是运行时参数的类型。如果用户传入的是单个参数，比如 Article 对象，此时
     * parameterType 为 Article.class。如果用户传入的多个参数，比如 [id = 1, author = "coolblog"]，
     * MyBatis 会使用 ParamMap 封装这些参数，此时 parameterType 为 ParamMap.class。如果
     * parameterType 有相应的 TypeHandler，这里则把 parameterType 设为 propertyType
     */
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property != null) {
        MetaClass metaClass = MetaClass.forClass(parameterType);
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          // 如果 property 为空，或 parameterType 是 Map 类型，则将 propertyType 设为 Object.class
          propertyType = Object.class;
        }
      } else {
        propertyType = Object.class;
      }
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
//     将propertyType赋值给javaType
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
//      遍历propertiesMap
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
//         如果用户明确配置了javaType,则以用户的配置为准。
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + parameterProperties);
        }
      }
      //#{age,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }
  
}
