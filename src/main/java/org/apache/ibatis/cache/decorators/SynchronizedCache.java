/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

/**
 * 同步的 Cache 实现类
 *
 * @author Clinton Begin
 */
public class SynchronizedCache implements Cache {

  /**
   * 装饰的 Cache 对象
   */
  private final Cache delegate;

  public SynchronizedCache(Cache delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public synchronized int getSize() { // 同步
    return delegate.getSize();
  }

  @Override
  public synchronized void putObject(Object key, Object object) { // 同步
    delegate.putObject(key, object);
  }

  @Override
  public synchronized Object getObject(Object key) { // 同步
    return delegate.getObject(key);
  }

  @Override
  public synchronized Object removeObject(Object key) { // 同步
    return delegate.removeObject(key);
  }

  @Override
  public synchronized void clear() { // 同步
    delegate.clear();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

}
