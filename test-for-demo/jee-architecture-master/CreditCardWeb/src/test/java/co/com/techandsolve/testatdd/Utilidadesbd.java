package co.com.techandsolve.testatdd;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Utilidadesbd {

	public static void ejecutarSentencia(String sentencia){
		Statement state = null;
		Connection conexion = null;
		try {
			
			Class.forName("com.mysql.jdbc.Driver");
			conexion= DriverManager.getConnection("jdbc:mysql://localhost:3306/cards", "root", "root");
			state =conexion.createStatement();
			state.executeUpdate(sentencia);
			
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}finally{
			
			try {
				state.close();
				conexion.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	

	

	
}
