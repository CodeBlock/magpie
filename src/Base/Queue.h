#pragma once

#include <iostream>

#include "Macros.h"

namespace magpie
{
  // A simple queue of items with a fixed capacity. Implemented using a
  // circular buffer. Enqueue and dequeue are O(1). Queue items must support
  // a default constructor and copying.
  template <class T, int Size>
  class Queue
  {
  public:
    Queue()
    : head_(0),
      count_(0) {}
    
    // Gets the number of items currently in the queue.
    int count() const { return count_; }
    
    // Gets whether or not the queue is empty.
    bool isEmpty() const { return count_ == 0; }
    
    // Gets the maximum number of items the queue can hold.
    int capacity() const { return Size; }
    
    // Clears the entire queue.
    void clear()
    {
      while (!isEmpty()) dequeue();
    }
    
    // Adds the given item to the head of the queue.
    void enqueue(const T& item)
    {
      ASSERT(count_ < capacity(), "Cannot enqueue a full queue.");
      
      items_[head_] = item;
      head_ = wrap(head_ + 1);
      count_++;
    }
    
    // Removes the tail item from the queue.
    T dequeue()
    {
      ASSERT(count_ > 0, "Cannot dequeue an empty queue.");
      
      int tail = wrap(head_ - count_);
      
      // Clear the item from the queue.
      T dequeued = items_[tail];
      items_[tail] = T();
      
      count_--;
      return dequeued;
    }
    
    // Gets the item at the given index in the queue. Index zero is the
    // next item which will be dequeued. Index one is the item after that,
    // etc.
    T& operator[] (int index)
    {
      ASSERT_INDEX(index, count_);
      
      return items_[wrap(head_ - count_ + index)];
    }
    
  private:
    inline int wrap(int index) const { return (index + Size) % Size; }
    
    int head_;
    int count_;
    T   items_[Size];
    
    NO_COPY(Queue);
  };
}

