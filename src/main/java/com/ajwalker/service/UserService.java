package com.ajwalker.service;

import com.ajwalker.constant.MailApis;
import com.ajwalker.dto.request.DologinRequestDto;
import com.ajwalker.dto.request.RegisterRequestDto;
import com.ajwalker.dto.request.UserAuthorisationDto;
import com.ajwalker.entity.User;
import com.ajwalker.exception.ErrorType;
import com.ajwalker.exception.HRAppException;
import com.ajwalker.mapper.UserMapper;
import com.ajwalker.repository.UserRepository;
import com.ajwalker.utility.Enum.user.EUserAuthorisation;
import com.ajwalker.utility.Enum.user.EUserState;
import com.ajwalker.utility.JwtManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor // bunu yazmazsak constructor injection yapmak gerekir
public class UserService {
	private final UserRepository userRepository;
	private final MailService mailService;
	private final UserAuthVerifyCodeService userAuthVerifyCodeService;
	private final JwtManager jwtManager;
	private final PasswordEncoder passwordEncoder;
	
	public Boolean register(RegisterRequestDto dto) {
		User user = UserMapper.INSTANCE.fromRegisterDto(dto);
		user.setUserState(EUserState.PENDING);
		user = userRepository.save(user);

		String authCode = userAuthVerifyCodeService.generateUserAuthVerifyCode(user.getId());
		String verificationLink = MailApis.VERIFICATION_LINK + authCode;
		mailService.sendMail(user.getEmail(), MailApis.VERIFICATION, verificationLink);
		return true;
	}
	
	public String doLogin(DologinRequestDto dto) {
		Optional<User> userOptional = userRepository.findOptionalByEmail(dto.email());

		if(!passwordEncoder.matches(dto.password(), userOptional.get().getPassword())){
			throw new HRAppException(ErrorType.INVALID_EMAIL_OR_PASSWORD);
		}

		if(userOptional.get().getUserState().equals(EUserState.PENDING)){
			return EUserState.PENDING.toString();
		}
		if(userOptional.get().getUserState().equals(EUserState.IN_REVIEW)){
			return EUserState.IN_REVIEW.toString();
		}
		if(userOptional.get().getUserState().equals(EUserState.DENIED)){
			throw new HRAppException(ErrorType.DENIED_USER);
		}
		String token  = jwtManager.createToken(userOptional.get().getId());
		return token;
	}

	public Optional<User> findUserById(Long userId) {
		return userRepository.findById(userId);
	}

	public void updateUserStateToInReView(Long userId) {
		Optional<User> userOptional = userRepository.findById(userId);
		if(userOptional.isPresent()) {
			User user = userOptional.get();
			user.setUserState(EUserState.IN_REVIEW);
			userRepository.save(user);
		}
	}

	public List<User> getAllCustomers() {
		return userRepository.findAllUserByUserState(List.of(EUserState.ACTIVE, EUserState.INACTIVE));
	}

	public List<User> getAllUserOnWait() {
		return userRepository.findAllUserByUserState(List.of(EUserState.PENDING,EUserState.IN_REVIEW));
	}

	public User userAuthorisation(UserAuthorisationDto dto) {
		Optional<User> userOptional = userRepository.findById(dto.userId());
		if(userOptional.isEmpty()) {
			throw new HRAppException(ErrorType.NOTFOUND_USER);
		}
		User user = userOptional.get();
		if(dto.answer().equalsIgnoreCase(EUserAuthorisation.ACCEPT.toString())) {
			return updateUserToInActive(user);
		}
		if(dto.answer().equalsIgnoreCase(EUserAuthorisation.DENY.toString())) {
			return updateUserToDenied(user);
		}
		return user;
	}
	private User updateUserToInActive(User user) {
		user.setUserState(EUserState.INACTIVE);
		return userRepository.save(user);
	}
	private User updateUserToDenied(User user) {
		user.setUserState(EUserState.DENIED);
		return userRepository.save(user);
	}
}