/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.autoconstructor;

import java.util.List;

import org.apache.ibatis.annotations.Select;

public interface AutoConstructorMapper {
  /* PrimitiveSubject */

  @Select("SELECT * FROM subject WHERE id = #{id}")
  PrimitiveSubject getSubject(final int id);

  @Select("SELECT * FROM subject")
  List<PrimitiveSubject> getSubjects();

  /* AnnotatedSubject */

  @Select("SELECT * FROM subject")
  List<AnnotatedSubject> getAnnotatedSubjects();

  /* BadSubject */

  @Select("SELECT * FROM subject")
  List<BadSubject> getBadSubjects();

  /* ExtensiveSubject */

  @Select("SELECT * FROM extensive_subject")
  List<ExtensiveSubject> getExtensiveSubjects();
}
