/*
 * Copyright 2013 Orient Technologies.
 * Copyright 2013 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.core.sql.model;

import com.orientechnologies.common.collection.OAlwaysGreaterKey;
import com.orientechnologies.common.collection.OAlwaysLessKey;
import com.orientechnologies.common.collection.OCompositeKey;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
public class OPath extends OExpressionWithChildren{
  
  public OPath(OExpression left, OExpression right) {
    this(null,left,right);
  }

  public OPath(String alias, OExpression left, OExpression right) {
    super(alias,left,right);
    if(alias == null){
        setAlias(left.getAlias());
    }
  }
  
  public OExpression getLeft(){
    return children.get(0);
  }
  
  public OExpression getRight(){
    return children.get(1);
  }
  
  @Override
  protected String thisToString() {
    return "(path)";
  }

  @Override
  protected Object evaluateNow(OCommandContext context, Object candidate) {
    final Object left = getLeft().evaluate(context, candidate);
    final Object right = getRight().evaluate(context, left);
    return right;
  }

  @Override
  public Object accept(OExpressionVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    return super.equals(obj);
  }
  
  @Override
  public OPath copy() {
    return new OPath(alias, getLeft(),getRight());
  }
  
  /**
   * Unfold this path.
   * Example : a.b.c.d
   * will return list : [a,b,c,d]
   * @param path
   * @param names
   * @return List<OName>
   */
  public List<OExpression> unfoldPath(){
      final List<OExpression> lst = new ArrayList<OExpression>();
      unfold(this, lst);
      return lst;
  }
  
  private void unfold(OExpression exp, List<OExpression> lst){
      if(exp instanceof OPath){
          unfold(((OPath)exp).getLeft(), lst);
          unfold(((OPath)exp).getRight(), lst);
      }else{
          lst.add(exp);
      }
  }
  
  public static Map.Entry<List<FoldSegment>,OClass> unfoldIndexes(List<OName> path, OClass clazz){
    final List<FoldSegment> walk = new ArrayList<FoldSegment>();
    for (int i = 0, n = path.size() - 1; i < n; i++) {
      final OName name = path.get(i);
      //path element
      final OProperty prop = clazz.getProperty(name.getName());
      if (prop == null || prop.getLinkedClass() == null) {
        //can't optimize
        return null;
      }
      final Set<OIndex<?>> indexes = clazz.getClassInvolvedIndexes(name.getName());
      if (indexes == null || indexes.isEmpty()) {
        //no index usable
        return null;
      }
      boolean found = false;
      for (OIndex index : indexes) {
        if(!index.getDefinition().getFields().get(0).equalsIgnoreCase(name.getName())){
          continue;
        }
        
        found = true;
        final FoldSegment segment = new FoldSegment();
        segment.index = index;
        segment.fieldName =name.getName();
        walk.add(segment);
      }
      if (!found) {
        //no index usable
        return null;
      }
      clazz = prop.getLinkedClass();
    }
    
    return new AbstractMap.SimpleImmutableEntry<List<FoldSegment>,OClass>(walk, clazz);
  }
  
  public static void foldIndexes(OExpressionAbstract exp, final List<FoldSegment> walk, final OSearchResult searchResult) {
    //unfold the path
    for (int i = walk.size() - 1; i >= 0; i--) {
      final FoldSegment wi = walk.get(i);
      final List<String> keyFields = wi.index.getDefinition().getFields();
      if(keyFields.size() == 1){
        if (searchResult.getIncluded() != null) {
          searchResult.setIncluded(wi.index.getValues(searchResult.getIncluded()));
        } else {
          searchResult.setExcluded(wi.index.getValues(searchResult.getExcluded()));
        }
        exp.updateStatistic(wi.index);
      }else{
        final Collection sublst;
        if (searchResult.getIncluded() != null) {
          sublst = searchResult.getIncluded();
        } else {
          sublst = searchResult.getExcluded();
        }
        
        final Collection search = new ArrayList();
        for(Object o : sublst){
          final Object[] fk = new Object[1];
          fk[0] = o;
          search.add(new OCompositeKey(fk));
        }
        
        if (searchResult.getIncluded() != null) {
          searchResult.setIncluded(wi.index.getValues(search));
        } else {
          searchResult.setExcluded(wi.index.getValues(search));
        }
        exp.updateStatistic(wi.index);
      }
    }
  }
  
  public static final class FoldSegment{
    
    private OIndex index;
    private String fieldName;
    
  }
}
