package ru.nekoguys.game.entity.user.model

data class User(
    val id: Id,
    val email: String,
    val password: String,
    val role: UserRole,
    val firstName: String?,
    val secondName: String?,
) {
    data class Id(val number: Long) {
        override fun toString() = number.toString()
    }

    // Методы toString, equals,
    override fun toString(): String {
        return "User(id=$id, email=$email, password=HIDDEN, role=$role, firstName=$firstName, secondName=$secondName)"
    }
}

sealed interface UserRole {
    sealed interface Student : UserRole {
        companion object Implementation : Student {
            override fun toString() = "Student"
        }
    }

    sealed interface Teacher : UserRole {
        companion object Implementation : Student, Teacher {
            override fun toString() = "Teacher"
        }
    }

    sealed interface Admin : UserRole {
        companion object Implementation : Student, Teacher, Admin {
            override fun toString() = "Admin"
        }
    }
}

