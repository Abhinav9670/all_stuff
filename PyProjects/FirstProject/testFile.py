import numpy as np

# 1) basics
list1 =  [1,2,3,4]
list2 = [5,6,7,8]
#list3 = [7,8,9]

#array = np.array([[list1,list2,list3]])
#print(array)

# 2) numpy attributes

#print(array.shape, "SHAPE")
#print(array.size, "SIZE")
#print(array.dtype, "DTYPE")
#print(array.ndim, "Dimension")


# 3) Array initiallization methods

#zero_arr = np.zeros((2,3))
#print(zero_arr)

#one_arr = np.ones((2,3))
#print(one_arr)

new_arr = np.array([list1,list2])
print(new_arr.data)
print(new_arr.shape)
print(new_arr.size)
print(new_arr.itemsize)