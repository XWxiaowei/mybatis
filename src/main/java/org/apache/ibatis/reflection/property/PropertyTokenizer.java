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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
/**
 * 属性分解为标记，迭代子模式
 * 如person[0].birthdate.year，将依次取得person[0], birthdate, year
 * 
 */
public class PropertyTokenizer implements Iterable<PropertyTokenizer>, Iterator<PropertyTokenizer> {
  //例子： person[0].birthdate.year
  private String name; //person
  private String indexedName; //person[0]
  private String index; //0
  private String children; //birthdate.year

  public PropertyTokenizer(String fullname) {
      //person[0].birthdate.year
      //找.（检测传入的参数中是否宝航了字符'.'）
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      /*
        以点位为界，进行分割。比如：
        fullname=com.jay.mybatis
        以第一个点为分界符：
        name=com
        children=jay.mybatis
       */
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
        //找不到.的话，取全部部分
      name = fullname;
      children = null;
    }
    indexedName = name;
    //把中括号里的数字给解析出来
    delim = name.indexOf('[');
    if (delim > -1) {
      /*
      * 获取中括号里的内容，比如：
      * 1. 对于数组或List集合：[]中的内容为数组下标，
      * 比如fullname=articles[1],index=1
      * 2.对于Map: []中的内容为键，
      * 比如 fullname=xxxMap[keyName],index=keyName
      *
      * 关于 index 属性的用法，可以参考 BaseWrapper 的 getCollectionValue 方法
      * */
      index = name.substring(delim + 1, name.length() - 1);
//      获取分解符前面的内容，比如 fullname=articles[1],name=articles
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  //取得下一个,非常简单，直接再通过儿子来new另外一个实例
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }

  @Override
  public Iterator<PropertyTokenizer> iterator() {
    return this;
  }
}
