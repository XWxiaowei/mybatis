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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * 默认参数处理器
 * 
 */
public class DefaultParameterHandler implements ParameterHandler {

  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;
  private final Object parameterObject;
  private BoundSql boundSql;
  private Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  //设置参数
  @Override
  public void setParameters(PreparedStatement ps) throws SQLException {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    /*
    * 从BoundSql中获取ParameterMapping列表，每个ParameterMapping
    * 与原始SQL中的#{xxx} 占位符一一对应
    * */
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings != null) {
      //循环设参数
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);
//       检测参数类型，排除掉mode为OUT类型的parameterMapping
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          //如果不是OUT，才设进去
          Object value;
//          获取属性名
          String propertyName = parameterMapping.getProperty();
//         检测BoundSql的additionalParameter是否包含propertyName
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            //若有额外的参数, 设为额外的参数
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {
            //若参数为null，直接设null
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            //若参数有相应的TypeHandler，直接设object
            value = parameterObject;
          } else {
            //除此以外，MetaObject.getValue反射取得值设进去
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
//          之上，获取#{xxx}占位符属性所对应的运行时参数
//          -------------------分割线-----------------------
//      之下，获取#{xxx}占位符属性对应的TypeHandler,并在最后通过TypeHandler将运行时参数值设置到
//          PreparedStatement中。
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          JdbcType jdbcType = parameterMapping.getJdbcType();
          if (value == null && jdbcType == null) {
            //不同类型的set方法不同，所以委派给子类的setParameter方法
            jdbcType = configuration.getJdbcTypeForNull();
          }
//        由类型处理器typeHandler向ParameterHandler设置参数
          typeHandler.setParameter(ps, i + 1, value, jdbcType);
        }
      }
    }
  }

}
