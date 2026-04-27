class Dog:

    def __init__(self, name,age, sex):
        self.name = name
        self.age = age
        self.sex = sex

    def get_name(self):
        return self.name

    def get_age(self):
        return self.age

    def get_sex(self):
        return self.sex

d= Dog("Tim","5","MALE")
print(d.get_name())
print(d.get_sex())
print(d.get_age())

