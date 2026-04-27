import numpy as np
y = [1,2,3,4,5]
#x= np.array(y)
#print(type(x))
#print(type(y))

l = []

ar3= np.array([[[1,2,3,4],
                [5,6,7,8],
                [9,10,11,12],
                [13,14,15,16],
                ]])



ar_zero = np.ones((5,8))
# in parantheses=> dimension, element => use any => zeros/ones

ar_rn = np.arange(4)

ar_dia = np.eye(6)

ar_lineSpace = np.linspace(1,10,num = 2)
# => start index, end index and number of elements (with continous gap)

random = np.random.rand(4)
#for one dimension
random_again = np.random.rand(2,5)

randomValuyes = np.random.randn(5)

ranfValues = np.random.ranf(5)

randInteger = np.random.randint(0,10,5)
# min, max and count
z = [6,7,8,9,10]

var = np.array(y)
let = np.array(z)


conVale = np.array([1,2,3,4,5,6,7,8,9,10,11,12])
nww = conVale.reshape(2,3,2)


fhg = np.array([[[1,2,3],[4,5,6]],[[7,8,9],[10,11,12]]])
fkj = np.array([[7,8,9],[10,11,12],[13,14,15]])
onedim = np.array([1,2,3,4,5,6,7,8,9])

x1 = np.where( onedim == 2 )

x2 = np.searchsorted(onedim,10)
print(x2)

indx = fhg[1,1,0:]

dobx = fkj[0,2:]

# slicing ==> fkj[select element to be sliced, start of slicing point, end of slicing point and skip point as well]


#for a in fhg:
    #for b in a:
      #  for c in b:
          #  print("done")





#for i in np.nditer(fhg):
#    print(i)


#for i,d in np.ndenumerate(fhg):
    #print(i,d)

