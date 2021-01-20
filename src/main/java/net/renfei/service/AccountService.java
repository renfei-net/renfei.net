package net.renfei.service;

import lombok.extern.slf4j.Slf4j;
import net.renfei.base.BaseService;
import net.renfei.config.RenFeiConfig;
import net.renfei.discuz.repository.*;
import net.renfei.discuz.repository.entity.*;
import net.renfei.discuz.ucenter.client.Client;
import net.renfei.entity.AccountDTO;
import net.renfei.entity.SignInVO;
import net.renfei.entity.SignUpVO;
import net.renfei.entity.UpdatePasswordVO;
import net.renfei.exceptions.NeedU2FException;
import net.renfei.exceptions.ServiceException;
import net.renfei.repository.*;
import net.renfei.repository.entity.*;
import net.renfei.sdk.utils.*;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * <p>Title: AccountService</p>
 * <p>Description: 账户服务</p>
 *
 * @author RenFei(i @ renfei.net)
 */
@Slf4j
@Service
public class AccountService extends BaseService {
    private final RenFeiConfig renFeiConfig;
    private final RoleDOMapper roleDOMapper;
    private final AccountDOMapper accountMapper;
    private final PermissionDOMapper permissionDOMapper;
    private final AccountRoleDOMapper accountRoleDOMapper;
    private final RolePermissionDOMapper rolePermissionDOMapper;
    private final AccountKeepNameDOMapper accountKeepNameMapper;
    private final VerificationCodeService verificationCodeService;
    private final DiscuzCommonMemberDOMapper discuzCommonMemberDOMapper;
    private final DiscuzUcenterMembersDOMapper discuzUcenterMembersMapper;
    private final DiscuzCommonMemberCountDOMapper discuzCommonMemberCountDOMapper;
    private final DiscuzCommonMemberStatusDOMapper discuzCommonMemberStatusDOMapper;
    private final DiscuzCommonMemberProfileDOMapper discuzCommonMemberProfileDOMapper;
    private final DiscuzCommonMemberFieldHomeDOMapper discuzCommonMemberFieldHomeDOMapper;
    private final DiscuzCommonMemberFieldForumDOMapper discuzCommonMemberFieldForumDOMapper;
    private final Pattern specialPattern = Pattern.compile("[ _`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]|\n|\r|\t");

    public AccountService(RenFeiConfig renFeiConfig,
                          RoleDOMapper roleDOMapper,
                          AccountDOMapper accountMapper,
                          PermissionDOMapper permissionDOMapper,
                          AccountRoleDOMapper accountRoleDOMapper,
                          RolePermissionDOMapper rolePermissionDOMapper,
                          AccountKeepNameDOMapper accountKeepNameMapper,
                          VerificationCodeService verificationCodeService,
                          DiscuzCommonMemberDOMapper discuzCommonMemberDOMapper,
                          DiscuzUcenterMembersDOMapper discuzUcenterMembersMapper,
                          DiscuzCommonMemberCountDOMapper discuzCommonMemberCountDOMapper,
                          DiscuzCommonMemberStatusDOMapper discuzCommonMemberStatusDOMapper,
                          DiscuzCommonMemberProfileDOMapper discuzCommonMemberProfileDOMapper,
                          DiscuzCommonMemberFieldHomeDOMapper discuzCommonMemberFieldHomeDOMapper,
                          DiscuzCommonMemberFieldForumDOMapper discuzCommonMemberFieldForumDOMapper) {
        this.renFeiConfig = renFeiConfig;
        this.roleDOMapper = roleDOMapper;
        this.accountMapper = accountMapper;
        this.permissionDOMapper = permissionDOMapper;
        this.accountRoleDOMapper = accountRoleDOMapper;
        this.rolePermissionDOMapper = rolePermissionDOMapper;
        this.accountKeepNameMapper = accountKeepNameMapper;
        this.verificationCodeService = verificationCodeService;
        this.discuzCommonMemberDOMapper = discuzCommonMemberDOMapper;
        this.discuzUcenterMembersMapper = discuzUcenterMembersMapper;
        this.discuzCommonMemberCountDOMapper = discuzCommonMemberCountDOMapper;
        this.discuzCommonMemberStatusDOMapper = discuzCommonMemberStatusDOMapper;
        this.discuzCommonMemberProfileDOMapper = discuzCommonMemberProfileDOMapper;
        this.discuzCommonMemberFieldHomeDOMapper = discuzCommonMemberFieldHomeDOMapper;
        this.discuzCommonMemberFieldForumDOMapper = discuzCommonMemberFieldForumDOMapper;
    }

    /**
     * 账户登录服务
     *
     * @param signInVO
     * @return
     */
    public AccountDTO signIn(SignInVO signInVO, HttpServletRequest request) {
        // 先获取用户，如果没有直接就返回错误
        AccountDOExample example = new AccountDOExample();
        AccountDOExample.Criteria criteria = example.createCriteria();
        if (signInVO == null || BeanUtils.isEmpty(signInVO.getUserName())) {
            throw new ServiceException("用户名或密码错误");
        }
        if (StringUtils.isChinaPhone(signInVO.getUserName())) {
            criteria.andPhoneEqualTo(signInVO.getUserName());
        } else if (StringUtils.isEmail(signInVO.getUserName())) {
            // 邮件登陆，那状态码必须大于等于1，1邮箱验证；2手机验证；3邮箱和手机都验证
            criteria.andEmailEqualTo(signInVO.getUserName());
        } else {
            criteria.andUserNameEqualTo(signInVO.getUserName());
        }
        AccountDO account = ListUtils.getOne(accountMapper.selectByExample(example));
        if (account == null) {
            throw new ServiceException("用户未注册，请先注册");
        }
        if (account.getStateCode() < 0) {
            throw new ServiceException("当前账户已被冻结，请联系我们解冻");
        }
        if (account.getStateCode() < 1) {
            // 发送激活邮件
            verificationCodeService.sendVerificationCode(true, DateUtils.nextHours(2),
                    account.getEmail(), "SIGN_UP", account, renFeiConfig.getDomain() + "/auth/signUp/activation");
            throw new ServiceException("当前账户邮箱未激活，我们已经为您发送了一封激活邮件");
        } else if (StringUtils.isChinaPhone(signInVO.getUserName()) && account.getStateCode() < 2) {
            // 邮件登陆，那状态码必须大于等于1，1邮箱验证；2手机验证；3邮箱和手机都验证
            throw new ServiceException("当前账户手机号码未通过验证，不能使用手机号码登录");
        }
        if (account.getLockTime() != null) {
            // 判断锁定时间
            if (new Date().before(account.getLockTime())) {
                String lockDate = DateUtils.getDate(account.getLockTime(), "yyyy-MM-dd hh:mm:ss");
                throw new ServiceException("当前账户已被锁定至[" + lockDate + "]，请稍后再试");
            }
        }
        if (!BeanUtils.isEmpty(account.getTotp()) && BeanUtils.isEmpty(signInVO.getTOtp())) {
            throw new NeedU2FException("当前账户开启了两步认证(U2F)，需要提供两步认证码");
        }
        AccountDTO accountDTO = new AccountDTO();
        if (signInVO.getUseVerCode()) {
            // 使用验证码验证
            VerificationCodeDO verificationCode = verificationCodeService.verificationCode(signInVO.getPassword(), signInVO.getUserName(), "SIGN_IN");
            if (verificationCode == null) {
                throw new ServiceException("验证码错误或已过期");
            }
        } else {
            // 使用密码登陆
            if (!PasswordUtils.verifyPassword(signInVO.getPassword(), account.getPassword())) {
                // 记录错误，如果错误超过6次，锁定时间为 (N-6)*1分钟
                account.setTrialErrorTimes(account.getTrialErrorTimes() + 1);
                if (account.getTrialErrorTimes() > 6) {
                    // 锁定时间
                    account.setLockTime(DateUtils.nextMinutes(account.getTrialErrorTimes() - 6));
                }
                accountMapper.updateByPrimaryKeySelective(account);
                throw new ServiceException("用户名或密码错误");
            }
        }
        // 两步认证
        if (!BeanUtils.isEmpty(account.getTotp())) {
            if (!GoogleAuthenticator.authcode(signInVO.getTOtp(), account.getTotp())) {
                throw new ServiceException("两步认证(U2F)失败，请重试");
            }
        }
        org.springframework.beans.BeanUtils.copyProperties(account, accountDTO);
        // 登陆论坛
        DiscuzUcenterMembersDOExample discuzUcenterMembersExample = new DiscuzUcenterMembersDOExample();
        discuzUcenterMembersExample.createCriteria().andUsernameEqualTo(account.getUserName());
        DiscuzUcenterMembersDO discuzUcenterMembers = ListUtils.getOne(discuzUcenterMembersMapper.selectByExample(discuzUcenterMembersExample));
        if (discuzUcenterMembers != null) {
            try {
                Client client =
                        new Client(renFeiConfig.getUCenter().getApi(),
                                null,
                                renFeiConfig.getUCenter().getKey(),
                                renFeiConfig.getUCenter().getAppId(),
                                renFeiConfig.getUCenter().getConnect());
                String script = client.ucUserSynlogin(discuzUcenterMembers.getUid());
                log.info("uc script:{}", script);
                if (!BeanUtils.isEmpty(script)) {
                    String[] strings = script.split("src=\"");
                    String script2 = "";
                    if (strings.length == 3) {
                        script2 += strings[1].replace("\" reload=\"1\"></script><script type=\"text/javascript\" ", "");
                        script2 += "|";
                        script2 += strings[2].replace("\" reload=\"1\"></script>", "");
                    } else if (strings.length == 2) {
                        script2 += strings[1].replace("\" reload=\"1\"></script>", "");
                    } else {
                        log.warn("strings.length != 3,script:{}", script);
                    }
                    // 将http转为https
                    script = script2.replace("http://", "https://");
                } else {
                    log.warn("根据UserName：{}，论坛登录脚本为空。", account.getUserName());
                }
                accountDTO.setUcScript(script);
            } catch (Exception exception) {
                log.error(exception.getMessage(), exception);
            }
        } else {
            log.warn("根据UserName：{}，未找到论坛用户，所以没有论坛登录脚本。", account.getUserName());
        }
        return accountDTO;
    }

    /**
     * 填充用户角色、权限
     *
     * @param accountDTO
     * @return
     */
    public AccountDTO fillRolePermissions(AccountDTO accountDTO) {
        if (accountDTO != null && !BeanUtils.isEmpty(accountDTO.getUuid())) {
            AccountRoleDOExample accountRoleExample = new AccountRoleDOExample();
            accountRoleExample.createCriteria().andAccountUuidEqualTo(accountDTO.getUuid());
            List<AccountRoleDO> accountRoleList = accountRoleDOMapper.selectByExample(accountRoleExample);
            if (!BeanUtils.isEmpty(accountRoleList)) {
                List<String> roleUuidList = new ArrayList<>(accountRoleList.size());
                accountRoleList.forEach(accountRoleDO -> roleUuidList.add(accountRoleDO.getRoleUuid()));
                // 查询所属角色列表
                RoleDOExample roleExample = new RoleDOExample();
                roleExample.createCriteria().andUuidIn(roleUuidList);
                List<RoleDO> roleList = roleDOMapper.selectByExample(roleExample);
                if (!BeanUtils.isEmpty(roleList)) {
                    List<String> roleEnNameList = new ArrayList<>(roleList.size());
                    roleList.forEach(roleDO -> roleEnNameList.add(roleDO.getRoleNameEn()));
                    accountDTO.setRoles(roleEnNameList);
                }
                // 查询角色对应的权限列表
                RolePermissionDOExample rolePermissionExample = new RolePermissionDOExample();
                rolePermissionExample.createCriteria().andRoleUuidIn(roleUuidList);
                List<RolePermissionDO> rolePermissionList = rolePermissionDOMapper.selectByExample(rolePermissionExample);
                if (!BeanUtils.isEmpty(rolePermissionList)) {
                    List<String> permissionUuidList = new ArrayList<>(rolePermissionList.size());
                    rolePermissionList.forEach(rolePermissionDO -> permissionUuidList.add(rolePermissionDO.getPermissionUuid()));
                    PermissionDOExample permissionExample = new PermissionDOExample();
                    permissionExample.createCriteria().andUuidIn(permissionUuidList);
                    List<PermissionDO> permissionList = permissionDOMapper.selectByExample(permissionExample);
                    if (!BeanUtils.isEmpty(permissionList)) {
                        List<String> permissionExpressionList = new ArrayList<>(permissionList.size());
                        permissionList.forEach(permissionDO -> permissionExpressionList.add(permissionDO.getExpression()));
                        accountDTO.setAuthorities(permissionExpressionList);
                    }
                }
            }
        }
        return accountDTO;
    }

    public AccountDTO getAccountDTOByUuid(String uuid) {
        AccountDOExample example = new AccountDOExample();
        example.createCriteria().andUuidEqualTo(uuid);
        AccountDO account = ListUtils.getOne(accountMapper.selectByExample(example));
        if (account == null) {
            throw new ServiceException("用户未注册，请先注册");
        }
        if (account.getStateCode() < 0) {
            throw new ServiceException("当前账户已被冻结，请联系我们解冻");
        }
        AccountDTO accountDTO = new AccountDTO();
        org.springframework.beans.BeanUtils.copyProperties(account, accountDTO);
        return fillRolePermissions(accountDTO);
    }

    /**
     * 发送登陆验证码
     */
    public void sendSignInVerificationCode(String userName) {
        if (BeanUtils.isEmpty(userName)) {
            throw new ServiceException("请输入要发送验证码的账户");
        }
        String userNa = userName.trim().toLowerCase();
        AccountDOExample example = new AccountDOExample();
        AccountDOExample.Criteria criteria = example.createCriteria();
        if (StringUtils.isEmail(userNa)) {
            criteria.andEmailEqualTo(userNa);
        } else if (StringUtils.isChinaPhone(userNa)) {
            criteria.andPhoneEqualTo(userNa);
        } else {
            throw new ServiceException("只有手机号码或电子邮箱才能使用验证码登陆");
        }
        AccountDO accountDO = ListUtils.getOne(accountMapper.selectByExample(example));
        if (accountDO == null) {
            throw new ServiceException("您输入的账号还没有注册，请先注册");
        }
        if (accountDO.getStateCode() < 1) {
            throw new ServiceException("当前账号已经冻结或未激活");
        }
        verificationCodeService.sendVerificationCode(false, DateUtils.nextMinutes(10), userNa, "SIGN_IN", accountDO, null);
    }

    /**
     * 账户注册服务
     *
     * @param signUpVO
     */
    public void signUp(SignUpVO signUpVO, HttpServletRequest request) throws PasswordUtils.CannotPerformOperationException {
        if (BeanUtils.isEmpty(signUpVO.getUserName().trim())) {
            throw new ServiceException("用户名不能为空。");
        }
        if (signUpVO.getUserName().trim().getBytes().length < 4) {
            throw new ServiceException("用户名长度过短，请重起一个名字吧。");
        }
        if (BeanUtils.isEmpty(signUpVO.getEmail().trim())) {
            throw new ServiceException("电子邮箱不能为空。");
        }
        if (StringUtils.isEmail(signUpVO.getUserName().trim())) {
            throw new ServiceException("您不能使用电子邮件地址作为您的用户名。");
        }
        if (StringUtils.isChinaPhone(signUpVO.getUserName().trim())) {
            throw new ServiceException("您不能使用手机号码作为您的用户名，注册成功后您可以绑定您的手机号码。");
        }
        if (StringUtils.isDomain(signUpVO.getUserName().trim())) {
            throw new ServiceException("用户名格式不正确，请换个用户名试试。");
        }
        if (specialPattern.matcher(signUpVO.getUserName().trim()).find()) {
            throw new ServiceException("用户名包含非法字符，请重起一个名字吧。");
        }
        if (BeanUtils.isEmpty(signUpVO.getPassword())) {
            throw new ServiceException("密码不能为空。");
        }
        if (!StringUtils.isEmail(signUpVO.getEmail().trim())) {
            throw new ServiceException("您填写的电子邮箱地址格式不正确。");
        }
        // 检查保留用户名
        AccountKeepNameDOExample keepNameExample = new AccountKeepNameDOExample();
        keepNameExample.createCriteria().andUserNameEqualTo(signUpVO.getUserName());
        List<AccountKeepNameDO> keepNameDOS = accountKeepNameMapper.selectByExample(keepNameExample);
        if (keepNameDOS != null && keepNameDOS.size() > 0) {
            throw new ServiceException("用户名已经被占用，请换个用户名试试。");
        }
        // 检查用户名重复
        AccountDOExample example = new AccountDOExample();
        example.createCriteria().andUserNameEqualTo(signUpVO.getUserName().trim().toLowerCase());
        AccountDO account = ListUtils.getOne(accountMapper.selectByExample(example));
        if (account != null) {
            throw new ServiceException("用户名已经被占用，请换个用户名试试。");
        }
        // 检查Email重复
        example = new AccountDOExample();
        example.createCriteria().andEmailEqualTo(signUpVO.getEmail().trim().toLowerCase());
        account = ListUtils.getOne(accountMapper.selectByExample(example));
        if (account != null) {
            throw new ServiceException("电子邮箱地址已经被注册，您不妨直接登陆试试。");
        }
        account = new AccountDO();
        account.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
        account.setUserName(signUpVO.getUserName().trim().toLowerCase());
        account.setEmail(signUpVO.getEmail().trim().toLowerCase());
        account.setPassword(PasswordUtils.createHash(signUpVO.getPassword()));
        account.setRegistrationDate(new Date());
        account.setRegistrationIp(IpUtils.getIpAddress(request));
        account.setStateCode(0);
        accountMapper.insertSelective(account);
        try {
            Client client =
                    new Client(renFeiConfig.getUCenter().getApi(),
                            null,
                            renFeiConfig.getUCenter().getKey(),
                            renFeiConfig.getUCenter().getAppId(),
                            renFeiConfig.getUCenter().getConnect());
            client.ucUserRegister(account.getUserName(), UUID.randomUUID().toString(), account.getEmail());
            // 向Discuz表里插入用户
            DiscuzUcenterMembersDOExample discuzUcenterMembersExample = new DiscuzUcenterMembersDOExample();
            discuzUcenterMembersExample.createCriteria().andUsernameEqualTo(account.getUserName());
            DiscuzUcenterMembersDO discuzUcenterMembers = ListUtils.getOne(discuzUcenterMembersMapper.selectByExample(discuzUcenterMembersExample));
            if (discuzUcenterMembers != null) {
                DiscuzCommonMemberDO commonMemberDO = new DiscuzCommonMemberDO();
                commonMemberDO.setUid(discuzUcenterMembers.getUid());
                commonMemberDO.setEmail(signUpVO.getEmail().trim().toLowerCase());
                commonMemberDO.setUsername(signUpVO.getUserName().trim().toLowerCase());
                commonMemberDO.setGroupid((short) 10);
                commonMemberDO.setRegdate(DateUtils.getUnixTimestamp());
                commonMemberDO.setTimeoffset("9999");
                commonMemberDO.setEmailstatus(1);
                discuzCommonMemberDOMapper.insertSelective(commonMemberDO);
                DiscuzCommonMemberCountDO commonMemberCountDO = new DiscuzCommonMemberCountDO();
                commonMemberCountDO.setUid(discuzUcenterMembers.getUid());
                discuzCommonMemberCountDOMapper.insertSelective(commonMemberCountDO);
                DiscuzCommonMemberFieldForumDOWithBLOBs commonMemberFieldForumDO = new DiscuzCommonMemberFieldForumDOWithBLOBs();
                commonMemberFieldForumDO.setUid(discuzUcenterMembers.getUid());
                commonMemberFieldForumDO.setMedals("");
                commonMemberFieldForumDO.setSightml("");
                commonMemberFieldForumDO.setGroupterms("");
                commonMemberFieldForumDO.setGroups("");
                discuzCommonMemberFieldForumDOMapper.insertSelective(commonMemberFieldForumDO);
                DiscuzCommonMemberFieldHomeDOWithBLOBs commonMemberFieldHomeDO = new DiscuzCommonMemberFieldHomeDOWithBLOBs();
                commonMemberFieldHomeDO.setUid(discuzUcenterMembers.getUid());
                commonMemberFieldHomeDO.setSpacecss("");
                commonMemberFieldHomeDO.setBlockposition("");
                commonMemberFieldHomeDO.setRecentnote("");
                commonMemberFieldHomeDO.setSpacenote("");
                commonMemberFieldHomeDO.setPrivacy("");
                commonMemberFieldHomeDO.setFeedfriend("");
                commonMemberFieldHomeDO.setAcceptemail("");
                commonMemberFieldHomeDO.setMagicgift("");
                commonMemberFieldHomeDO.setStickblogs("");
                discuzCommonMemberFieldHomeDOMapper.insertSelective(commonMemberFieldHomeDO);
                DiscuzCommonMemberProfileDOWithBLOBs commonMemberProfileDO = new DiscuzCommonMemberProfileDOWithBLOBs();
                commonMemberProfileDO.setUid(discuzUcenterMembers.getUid());
                commonMemberProfileDO.setBio("");
                commonMemberProfileDO.setInterest("");
                commonMemberProfileDO.setField1("");
                commonMemberProfileDO.setField2("");
                commonMemberProfileDO.setField3("");
                commonMemberProfileDO.setField4("");
                commonMemberProfileDO.setField5("");
                commonMemberProfileDO.setField6("");
                commonMemberProfileDO.setField7("");
                commonMemberProfileDO.setField8("");
                discuzCommonMemberProfileDOMapper.insertSelective(commonMemberProfileDO);
                DiscuzCommonMemberStatusDO commonMemberStatusDO = new DiscuzCommonMemberStatusDO();
                commonMemberStatusDO.setUid(discuzUcenterMembers.getUid());
                commonMemberStatusDO.setRegip(IpUtils.getIpAddress(request));
                commonMemberStatusDO.setLastip(IpUtils.getIpAddress(request));
                commonMemberStatusDO.setLastvisit(DateUtils.getUnixTimestamp());
                commonMemberStatusDO.setLastactivity(DateUtils.getUnixTimestamp());
                commonMemberStatusDO.setLastsendmail(0);
                commonMemberStatusDO.setInvisible(0);
                commonMemberStatusDO.setBuyercredit((short) 0);
                commonMemberStatusDO.setSellercredit((short) 0);
                commonMemberStatusDO.setFavtimes(0);
                commonMemberStatusDO.setSharetimes(0);
                commonMemberStatusDO.setProfileprogress((byte) 0);
                discuzCommonMemberStatusDOMapper.insertSelective(commonMemberStatusDO);
            }
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
        }
        // 发送激活邮件
        verificationCodeService.sendVerificationCode(true, DateUtils.nextHours(2),
                account.getEmail(), "SIGN_UP", account, renFeiConfig.getDomain() + "/auth/signUp/activation");
    }

    /**
     * 账户激活
     */
    public void signUpActivation(String code) throws ServiceException {
        VerificationCodeDO verificationCode = verificationCodeService.verificationCode(code, "SIGN_UP");
        if (verificationCode == null) {
            throw new ServiceException("验证码错误或已经失效");
        }
        if (BeanUtils.isEmpty(verificationCode.getAccountUuid())) {
            log.error("验证码不包含账户UUID，无法激活账户");
            throw new ServiceException("验证码错误或已经失效");
        }
        AccountDOExample example = new AccountDOExample();
        example.createCriteria()
                .andUuidEqualTo(verificationCode.getAccountUuid())
                .andStateCodeEqualTo(0);
        AccountDO account = ListUtils.getOne(accountMapper.selectByExample(example));
        if (account == null) {
            log.error("无法激活账户,UUID:{}不存在或账户状态不正确", verificationCode.getAccountUuid());
            throw new ServiceException("验证码错误或已经失效");
        }
        if (!verificationCode.getAddressee().equals(account.getEmail())) {
            log.error("无法激活账户,验证码的归属:{}与账户登记的：{}不一致", verificationCode.getAddressee(), account.getEmail());
            throw new ServiceException("验证码错误或已经失效");
        }
        account.setStateCode(1);
        accountMapper.updateByPrimaryKeySelective(account);
    }

    /**
     * 登出，返回Discuz的登出代码
     *
     * @param accountDTO
     * @return
     */
    public String signOut(AccountDTO accountDTO) {
        DiscuzUcenterMembersDOExample discuzUcenterMembersExample = new DiscuzUcenterMembersDOExample();
        discuzUcenterMembersExample.createCriteria().andUsernameEqualTo(accountDTO.getUserName());
        DiscuzUcenterMembersDO discuzUcenterMembers = ListUtils.getOne(discuzUcenterMembersMapper.selectByExample(discuzUcenterMembersExample));
        if (discuzUcenterMembers != null) {
            try {
                Client client =
                        new Client(renFeiConfig.getUCenter().getApi(),
                                null,
                                renFeiConfig.getUCenter().getKey(),
                                renFeiConfig.getUCenter().getAppId(),
                                renFeiConfig.getUCenter().getConnect());
                return client.ucUserSynlogout();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public AccountDO getAccountByEmail(String email) {
        if (BeanUtils.isEmpty(email)) {
            return null;
        }
        if (!StringUtils.isEmail(email.trim().toLowerCase())) {
            return null;
        }
        AccountDOExample example = new AccountDOExample();
        example.createCriteria().andEmailEqualTo(email.trim().toLowerCase());
        return ListUtils.getOne(accountMapper.selectByExample(example));
    }

    public AccountDO getAccountByPhone(String phone) {
        if (BeanUtils.isEmpty(phone)) {
            return null;
        }
        if (!StringUtils.isChinaPhone(phone.trim().toLowerCase())) {
            return null;
        }
        AccountDOExample example = new AccountDOExample();
        example.createCriteria().andPhoneEqualTo(phone.trim().toLowerCase());
        return ListUtils.getOne(accountMapper.selectByExample(example));
    }

    public void updateAccount(AccountDTO accountDTO) {
        AccountDO accountDO = new AccountDO();
        org.springframework.beans.BeanUtils.copyProperties(accountDTO, accountDO);
        accountMapper.updateByPrimaryKey(accountDO);
    }

    public String updatePassword(AccountDTO accountDTO, UpdatePasswordVO updatePasswordVO) throws PasswordUtils.CannotPerformOperationException {
        AccountDO accountDO = new AccountDO();
        org.springframework.beans.BeanUtils.copyProperties(accountDTO, accountDO);
        if (PasswordUtils.verifyPassword(updatePasswordVO.getOldPwd(), accountDTO.getPassword())) {
            String newPwd = PasswordUtils.createHash(updatePasswordVO.getNewPwd());
            accountDO.setPassword(newPwd);
            accountMapper.updateByPrimaryKeySelective(accountDO);
            return newPwd;
        } else {
            throw new ServiceException("当前密码不正确");
        }
    }
}
