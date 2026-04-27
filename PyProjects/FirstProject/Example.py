class Microwave:
    def __init__(self, brand:str,power_rating:str):
        self.brand = brand
        self.power_rating = power_rating
        self.turned_on = False

    def turn_on(self) -> None:
            if self.turned_on:
                print(f"Already Turned On ({self.brand})")
            else:
                self.turned_on = True
                print(f"Turned On ({self.brand}) now")

    def turn_off(self) -> None:
        if self.turned_on:
            self.turned_on = False
            print(f"Turned Off ({self.brand})")
        else:
            print(f"Already Turned Off ({self.brand})")


    def run (self, seconds:int) -> None:
        if self.turned_on:
            print(f'Running ({self.brand}) for {seconds} seconds')
        else:
            print(f'Turn on your microwave first ({self.brand})')


    def __add__(self, other):
        return f'{self.brand} + {other.brand}'

    def __str__(self):
        return f'{self.brand} {self.power_rating}'
    
    def __repr__(self):
        return f'{self.brand} {self.power_rating}'


smeg = Microwave(brand="Smeg",power_rating="10")
bosch = Microwave(brand="Bosch", power_rating="20")
print(smeg)
print(bosch)

