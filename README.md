# Simple Banking App for DB Transactions

This is a simple project to demonstrate the usage and pitfalls of database transactions specially in a multi-users environment.

### How to use this project?

In order to run this application, you have to set up a connection pool with the help of **JNDI** under the name of `jdbc/dep9-banking`.
You can find the database script for this project under the `resource` folder.

### Things to look for,

1. What happens if a person makes a deposit and at the same time someone transfers money to his account?
2. What happens if a person withdraws money from account and at the same time if he tries to transfer his money to another account?
3. What happens if two transfers happen at the same time while one is crediting and other is deducting?
4. What happens if two withdraws happen at the same time?
5. What happens if two deposits happen at the same time?

### version
1.0.0

### License
Copyright &copy; 2022. All Right Reserved.<br>
This project is licensed under the [MIT License](LICENSE.txt)

### Contact Details

* Email: visalsrimanga@gmail.com
* Linkedin: Visal Srimanga

